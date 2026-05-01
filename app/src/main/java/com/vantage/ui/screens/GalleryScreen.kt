package com.vantage.ui.screens

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vantage.models.PhotoPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    onPhotoClick: (List<PhotoPair>, Int) -> Unit,
    refreshTrigger: Any? = null
) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf<List<PhotoPair>>(emptyList()) }

    LaunchedEffect(refreshTrigger) {
        photos = loadVantagePhotos(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "Gallery",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (photos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No photos yet",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 16.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(photos.size) { index ->
                    val pair = photos[index]
                    AsyncImage(
                        model = pair.enhancedUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onPhotoClick(photos, index) }
                    )
                }
            }
        }
    }
}

private suspend fun loadVantagePhotos(context: Context): List<PhotoPair> = withContext(Dispatchers.IO) {
    val photos = mutableListOf<PhotoPair>()
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED
    )
    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
    val selectionArgs = arrayOf("DCIM/Vantage/")
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    try {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            val allFiles = mutableMapOf<String, Pair<Uri, Long>>()
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val date = cursor.getLong(dateCol)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                allFiles[name] = Pair(uri, date)
            }

            Log.d("Vantage", "Gallery: found ${allFiles.size} files in DCIM/Vantage/")

            // Only consider exact-match enhanced files (skip MediaStore collision copies like "_enhanced (1).jpg")
            val enhanced = allFiles.filter {
                it.key.contains("_enhanced") && !it.key.contains("(")
            }
            val originals = allFiles.filter { !it.key.contains("_enhanced") }
            val pairedOriginals = mutableSetOf<String>()

            for ((enhancedName, enhancedData) in enhanced) {
                val originalName = enhancedName.replace("_enhanced", "")
                val originalData = allFiles[originalName]
                if (originalData != null) {
                    photos.add(PhotoPair(originalData.first, enhancedData.first, enhancedData.second))
                    pairedOriginals.add(originalName)
                } else {
                    photos.add(PhotoPair(enhancedData.first, enhancedData.first, enhancedData.second))
                }
            }

            for ((originalName, originalData) in originals) {
                if (originalName !in pairedOriginals) {
                    photos.add(PhotoPair(originalData.first, originalData.first, originalData.second))
                }
            }
        }
    } catch (e: Exception) {
        Log.e("Vantage", "Gallery query failed", e)
    }

    Log.d("Vantage", "Gallery: returning ${photos.size} photo pairs")
    photos
}
