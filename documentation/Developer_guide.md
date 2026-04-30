# **Google x Qualcomm Hackathon: Developer Guide**

This technical guide provides a comprehensive workflow for developers deploying the Generative AI and Classical ML models using LiteRT and LiteRT-LM.

**Table of Contents**

[Google x Qualcomm Hackathon: Developer Guide](#heading=)

[Pre-work](#pre-work)

[Core Architectural Concepts](#core-architectural-concepts)

[LiteRT vs. LiteRT-LM](#litert-vs.-litert-lm)

[Backend Stack Hierarchy](#backend-stack-hierarchy)

[Hackathon Tracks](#hackathon-tracks)

[Models](#models)

[Additional References](#additional-references)

* [Device Details](#device-details)  
* [Track 1: LiteRT-LM for Generative AI  (e.g, Gemma3 1B & FastVLM)](#track-1:-litert-lm-for-generative-ai-\(e.g,-gemma4-e2b-&-fastvlm\))  
* [Track 2: LiteRT for Classical Models](#heading=)  
* [Details for Leverage Google AI Edge Gallery App](#details-for-leverage-google-ai-edge-gallery-app)

## **Pre-work** {#pre-work}

Suggest participants try out the samples below to get acquainted with LiteRT and LiteRT-LM

1. Try out the Qualcomm LiteRT & LiteRT-LM [sample apps](https://github.com/google-ai-edge/litert-samples/tree/main/compiled_model_api/qualcomm) with LiteRT and LiteRT-LM. Additional samples to learn more about LiteRT CPU/GPU/NPU(jit) can be found here: [Sample Apps](https://github.com/google-ai-edge/litert-samples/tree/main/compiled_model_api/image_segmentation)  
   * Models used in the sample apps can be found on the following Hugging Face collection: [https://huggingface.co/collections/litert-community/qualcomm](https://huggingface.co/collections/litert-community/qualcomm)  
2. Go over the documentation  
   * [https://ai.google.dev/edge/litert-lm](https://ai.google.dev/edge/litert-lm) for Track 1  
   * [https://ai.google.dev/edge/litert](https://ai.google.dev/edge/litert) for Track 2 

## **Core Architectural Concepts** {#core-architectural-concepts}

Understanding the relationship between the runtime and its specialized wrapper is essential for efficient deployment.

## **LiteRT vs. LiteRT-LM** {#litert-vs.-litert-lm}

* **LiteRT:** Serves as the foundational high-performance runtime for on-device machine learning, executing .tflite models across various hardware.  
* **LiteRT-LM:** A specialized high-level API wrapper powered by LiteRT specifically for Large Language Models (LLMs) and Vision-Language Models (VLMs). It automates complex tasks such as tokenization, KV-caching, and multimodal input handling. LiteRT-LM executes .litertlm files. 

## **Backend Stack Hierarchy** {#backend-stack-hierarchy}

LiteRT optimizes performance by offloading computation to available hardware:

| Backend | Capability | Optimal Use Case |
| :---- | :---- | :---- |
| CPU | Universal fallback for all devices | Low-complexity models or non-accelerated hardware. |
| GPU | Parallel task acceleration | Standard Android performance boost. |
| NPU (QNN) | High-efficiency hardware acceleration | Snapdragon 8 Elite (SM8750) for maximum speed. |

# **Hackathon Tracks** {#hackathon-tracks}

**About the challenge**

Participants will choose from one of the following tracks, each designed to minimize setup friction and maximize time spent building meaningful AI experiences. Each team of 3-5 will receive one Samsung Galaxy S25 Ultra to use for the duration of the hackathon.

**Track 1: LLM-Based Consumer Use Journeys with LiteRT-LM**

Build on-device LLM experiences using  Generative AI models with a focus on real consumer use cases.

**What’s provided:**

* Ready-to-use the Generative AI models, Gemma, EmbeddingGemma, and FastVLM, available via [LiteRT Hugging Face](https://huggingface.co/collections/litert-community/qualcomm)

* A reference [sample app](https://github.com/google-ai-edge/litert-samples/tree/main/compiled_model_api/qualcomm/gemma4) for Gemma 4 2B model 

This track is ideal for developers interested in integrating Generative AI-powered features into Android applications with on-device inference.

**Track 2: Classical Models — Vision & Audio with LiteRT**

Build efficient, on-device AI applications using classical ML models for vision or audio workloads.

**What’s provided:**

* Ready-to-use models that do not require **quantization** or **conversion**  
* NPU-friendly models available via [Qualcomm AI Hub](https://aihub.qualcomm.com/models) and [LiteRT HuggingFace](https://huggingface.co/collections/litert-community/qualcomm)   
* A sample application available through the LiteRT repository under the LiteRT-Samples github repo Qualcomm [directory](https://github.com/google-ai-edge/litert-samples/tree/main/compiled_model_api/qualcomm) (mobilenet v2, object\_detection), [EmbeddingGemma](https://github.com/google-ai-edge/litert-samples/tree/main/compiled_model_api/semantic_similarity), and [image segmentation](https://github.com/google-ai-edge/litert-samples/tree/main/compiled_model_api/image_segmentation) 

This track is ideal for developers looking to deploy performant vision or audio pipelines on Snapdragon NPUs with minimal overhead.

# **Models**  {#models}

[https://huggingface.co/collections/litert-community/qualcomm](https://huggingface.co/collections/litert-community/qualcomm)

**Track 1: Generative Models via LiteRT-LM**

[Gemma4 E2B](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/tree/main) ([Sample app for NPU](https://github.com/google-ai-edge/litert-samples/tree/main/compiled_model_api/qualcomm/llm_chatbot_npu) / [Sample app for CPU/GPU](https://github.com/google-ai-edge/litert-samples/tree/main/compiled_model_api/qualcomm/gemma4/cpu_gpu))  
Gemma 4 E2B is a multimodal "edge" model released by Google DeepMind in April 2026\. The "E" stands for Effective parameters, meaning it uses a specialized architecture to achieve high performance with a low memory footprint suitable for local, on-device deployment.

* **gemma-4-E2B-it\_qualcomm\_sm8750.litertlm:** Specifically compiled for the Snapdragon 8 Elite NPU.  
* **gemma-4-E2B-it.litertlm:** Designed for standard GPU and CPU acceleration.

[FastVLM-0.5B](https://huggingface.co/litert-community/FastVLM-0.5B) (Sample app for CPU/GPU/NPU)  
FastVLM is a multimodal vision-language model capable of interpreting image data and generating text responses.

* **FastVLM-0.5B.qualcomm.sm8750.litertlm** Specifically compiled for the Snapdragon 8 Elite NPU.

[Gemma3 1B](https://huggingface.co/litert-community/Gemma3-1B-IT/tree/main)   
Gemma 3 1B is a lightweight, open-weight language model from Google, optimized for on-device, mobile, and web applications. Released in March 2025, this 1-billion-parameter model offers high efficiency for text-based tasks.

* **Gemma3-1B-IT\_q4\_ekv1280\_sm8750.litertlm** Specifically compiled for the Snapdragon 8 Elite NPU.  
* **Gemma3-1b-it-int4.litertlm** Designed for standard GPU and CPU acceleration.

[Function Gemma](https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions)  
FunctionGemma is a specialized 270M parameter model based on Gemma 3, fine-tuned for efficient function calling and API action generation in local, on-device AI applications. It converts natural language into structured function calls, providing high performance for mobile/IoT devices with low latency. (Gallery App [mobileactions](https://github.com/google-ai-edge/gallery/tree/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/mobileactions), [tinygarden](https://github.com/google-ai-edge/gallery/tree/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/tinygarden))

* **Mobile\_actions\_q8\_ekv1024.litertlm** Designed for standard GPU and CPU acceleration.

**Track2: [LiteRT](https://github.com/google-ai-edge/LiteRT/tree/main), Vision and Audio (aka: traditional/classical models)** 

For more models: [https://aihub.qualcomm.com/apps?os=Android](https://aihub.qualcomm.com/apps?os=Android)

| Models / Demo Page | CPU/GPU/NPU (JIT) | Sample App |
| ----- | :---: | :---: |
| [EmbeddingGemma-300m](https://huggingface.co/litert-community/embeddinggemma-300m) | Supported | [Sample App](https://github.com/google-ai-edge/litert-samples/tree/main/compiled_model_api/semantic_similarity/build_from_source)  |
| [Image Segmentation](https://huggingface.co/litert-community/MediaPipe-Selfie-Segmentation/blob/main/selfie_multiclass.tflite) | Supported | [Sample App (CPU/GPU](https://github.com/google-ai-edge/litert-samples/tree/main/compiled_model_api/image_segmentation/kotlin_cpu_gpu))  [Sample App (NPU](https://github.com/google-ai-edge/litert-samples/tree/main/compiled_model_api/image_segmentation/kotlin_npu))  |
| [Image Classification](https://huggingface.co/qualcomm/MobileNet-v2) | Supported | [Sample App](https://github.com/google-ai-edge/litert-samples/tree/main/compiled_model_api/qualcomm/mobilenet_v2)  |
| [Object Detection](https://huggingface.co/litert-community/efficientdet) | Supported | [Sample App](https://github.com/google-ai-edge/litert-samples/tree/main/compiled_model_api/qualcomm/object_detection/efficientdet/kotlin_npu) |

Note: Migrating guide from TensorFlow Lite to LiteRT \- [link](https://ai.google.dev/edge/litert/migration)

# **Additional References** {#additional-references}

Applicable if you are not starting with a sample app.

# **Device Details** {#device-details}

* **Type:** Mobile  
* **Name:** Samsung Galaxy S25 Ultra  
* **Chipset:** Snapdragon® 8 Elite for Galaxy | SM8750-AC  
* **Hexagon Version and SOC Model:** v79 and 69, respectively

# **Track 1: LiteRT-LM for Generative AI  (e.g, Gemma4 E2B & FastVLM)** {#track-1:-litert-lm-for-generative-ai-(e.g,-gemma4-e2b-&-fastvlm)}

This guide details the LiteRT-LM ([repo](https://github.com/google-ai-edge/LiteRT-LM)) Developer Workflow for implementing machine learning inference in Android applications using the CompiledModel API. The following documentation provides instructions and code snippets for setting up and running models using various hardware accelerators, including CPU, GPU, and NPU, and outlines the core steps for creating an environment, configuring options, and performing inference.

The development workflow can be separated into the following two major entry points: 1\) Use our Kotlin API with Maven package; 2\) Use our C++ API and build from source

## **Kotlin API with Maven Package**

Please follow the [instructions](https://ai.google.dev/edge/litert-lm/android) in the official documentation to set up your project for importing the LiteRT-LM Maven package.

To run the models on NPU, please make sure you copy the NPU libraries from [the sample app](https://github.com/google-ai-edge/litert-samples/tree/main/compiled_model_api/qualcomm/llm_chatbot_npu/app/src/main/jniLibs/arm64-v8a) to your app folder. The required libraries are:

* `libLiteRtDispatch_Qualcomm.so`  
* `libQnnHtp.so`  
* `libQnnHtpV79Skel.so`  
* `libQnnHtpV79Stub.so`  
* `libQnnSystem.so`

Separately, you’d also need to copy the prebuilt `libGemmaModelConstraintProvider.so` from the [LiteRT-LM/prebuilt](https://github.com/google-ai-edge/LiteRT-LM/tree/main/prebuilt) folder (from the corresponding platform) to your app folder.

This [repo](https://github.com/google-ai-edge/litert-samples/tree/main/compiled_model_api/qualcomm/llm_chatbot_npu) gives you an example sample app that uses the Gemma4 model with NPU.

### **1\. Setting Up the Engine**

The *Engine* is the brain that runs your model. You need to provide a configuration that tells it where your model file is located and which hardware (like the NPU for speed) to use for both text and images.

```kotlin
val config = EngineConfig(
   modelPath = "/path/to/gemma-4-E2B-it_qualcomm_sm8750.litertlm
",
   backend = Backend.NPU(nativeLibDir), // Use NPU for text
   visionBackend = Backend.GPU(),       // Use GPU for images
   audioBackend = Backend.CPU()         // Use CPU for audio
)
val engine = Engine(config)
engine.initialize()
```

### **2\. Creating a Conversation**

A *Conversation* object helps the AI remember what was said earlier in the chat. You create it directly from your initialized engine.

```kotlin
val conversation = engine.createConversation()
```

### **3\. Sending Messages with Images**

Finally, you can send a message that includes both an image and a text question. The AI will process these together and send back the response in small pieces (chunks) that you can print or show in your app.

```kotlin
val userMessage = Message.user(
    Contents.of(
        Content.ImageFile("/path/to/photo.jpg"),
        Content.Text("What is in this image?")
    )
)
conversation.sendMessageAsync(userMessage).collect { response ->
    val chunk = response.contents.contents
        .filterIsInstance<Content.Text>()
        .joinToString("") { it.text }
    println(chunk)
}
```

## **C++ API and Build from Source**

Please follow the [instructions](https://ai.google.dev/edge/litert/next/litert_lm_npu#quick-start) in the official documentation to set up the environment and build the program. Next, use the following code snippets to start playing with it. 

```c

#include "runtime/engine/engine.h"

// ...

// 1. Define model assets and engine settings.
auto model_assets = ModelAssets::Create(model_path);
CHECK_OK(model_assets);

auto engine_settings = EngineSettings::CreateDefault(
    model_assets,
    /*backend=*/litert::lm::Backend::CPU);

// 2. Create the main Engine object.
absl::StatusOr<std::unique_ptr<Engine>> engine = Engine::CreateEngine(engine_settings);
CHECK_OK(engine);

// 3. Create a Conversation
auto conversation_config = ConversationConfig::CreateDefault(**engine);
CHECK_OK(conversation_config)
absl::StatusOr<std::unique_ptr<Conversation>> conversation = Conversation::Create(**engine, *conversation_config);
CHECK_OK(conversation);

// 4. Send message to the LLM with blocking call.
absl::StatusOr<Message> model_message = (*conversation)->SendMessage(
    JsonMessage{
        {"role", "user"},
        {"content", "What is the tallest building in the world?"}
    });
CHECK_OK(model_message);

// 5. Print the model message.
std::cout << *model_message << std::endl;

// 6. Send message to the LLM with asynchronous call
// where CreatePrintMessageCallback is a users implemented callback that would
// process the message once a chunk of message output is received.
std::stringstream captured_output;
(*conversation)->SendMessageAsync(
    JsonMessage{
        {"role", "user"},
        {"content", "What is the tallest building in the world?"}
    },
    CreatePrintMessageCallback(std::stringstream& captured_output)
);
// Wait until asynchronous finish or timeout.
*engine->WaitUntilDone(absl::Seconds(10));
```

# 

# **Track 2: LiteRT for Classical Models** 

# (e.g., Image Segmentation)

The **LiteRT CompiledModel API** is used for smaller, classical machine learning models (like image classification or segmentation) that don't require the specialized features of LiteRT-LM (Type 1). This is the standard way to deploy .tflite models.

## **Using CompiledModel API with CPU/GPU acceleration**

This section shows how to use LiteRT's CompiledModel API with CPU and GPU acceleration It talks about the two most common Android integration paths:

1. Kotlin / Android runtime (com.google.ai.edge.litert:litert)  
2. Android NDK / C++ with the prebuilt LiteRT C++ SDK

CompiledModel is a runtime entry for LiteRT. In the current API, the core flow is:

1. Create one shared Environment.  
2. Create Options and select CPU or GPU acceleration.  
3. Configure backend-specific CpuOptions or GpuOptions if needed.  
4. Create a CompiledModel.  
5. Create input/output TensorBuffers and run inference.

### **1\. Setup and Dependencies**

First, you need an Android project (which requires Android Studio and Kotlin knowledge).

#### **Gradle Dependency**

Add the core LiteRT library to your build.gradle.kts file. This handles both CPU and GPU acceleration by default for Kotlin users.

```kotlin
dependencies {
    // Core LiteRT library
    implementation("com.google.ai.edge.litert:litert:2.1.4")
}
```

### 

### **2\. Android release artifacts to use for v2.1.4**

For Android apps using the LiteRT Android binding, use:

implementation("com.google.ai.edge.litert:litert:2.1.4")

This is pretty much the simplest way to include GPU support on Android. For Kotlin users, the GPU accelerator is already bundled in the LiteRT Maven package.

You can then select CPU vs GPU in Kotlin by using CompiledModel.Options(...), see the following code snippet

```kotlin
val env = Environment.create()

val cpuModel =
    CompiledModel.create(
        context.assets,
        "model.tflite",
        CompiledModel.Options(Accelerator.CPU),
        env,
    )

val gpuModel =
    CompiledModel.create(
        context.assets,
        "model.tflite",
        CompiledModel.Options(Accelerator.GPU),
        env,
    )
```

 

### **3\. Model Loading and Configuration**

Models like image segmentation typically use a standard .tflite format. You must load this model and select your desired hardware accelerator (CPU, GPU, or NPU).

#### **Model Loading (Kotlin)**

```kotlin
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Accelerator

// 1. Select the accelerator
val options = CompiledModel.Options.builder()
    .setAccelerator(Accelerator.GPU) // Choose GPU for a standard speed boost
    .build()

// 2. Load the model from your app's assets folder
// The model must be packaged in your project (e.g., in the 'assets' folder).
val compiledModel = CompiledModel.create(
    context.assets,
    "segmentation_model.tflite", // Replace with your model file name
    options
)
```

### 

### **4\. Running Inference**

The execution flow involves preparing input data, creating buffers, running the model, and reading the results. **Crucially, heavy operations like this should always be run off the main application thread (e.g., using Kotlin Coroutines).**  
The basic execution flow is the same for CPU and GPU once the model has been  
Compiled.

#### **CPU / GPU / NPU execution & Running Inference**

```kotlin
// 1. Load model and initialize runtime.
val model =
    CompiledModel.create(
        context.assets,
        "model/mymodel.tflite",
        CompiledModel.Options(Accelerator.CPU, Accelerator.GPU)
    )

// 2. Pre-allocate input/output buffers
val inputBuffers = model.createInputBuffers()
val outputBuffers = model.createOutputBuffers()

// 3. Fill the first input
inputBuffers[0].writeFloat(...)

// 4. Invoke
model.run(inputBuffers, outputBuffers)

// 5. Read the output
val outputFloatArray = outputBuffers[0].readFloat()
```

## **Setup with NPU acceleration**

To run classic models on NPU with JIT mode, please make sure you copy the NPU libraries from [the sample app](https://github.com/google-ai-edge/litert-samples/tree/main/compiled_model_api/qualcomm/mobilenet_v2/app/src/main/jniLibs/arm64-v8a) to your app folder. The required libraries are:

* `libLiteRtCompilerPlugin_Qualcomm.so`  
* `libLiteRtDispatch_Qualcomm.so`  
* `libQnnHtp.so`  
* `libQnnHtpPrepare.so`  
* `libQnnHtpV79Skel.so`  
* `libQnnHtpV79Stub.so`  
* `libQnnSystem.so`

In production, it’s recommended to use Google Play Feature Delivery to ship NPU libraries only to targeted devices. See [https://ai.google.dev/edge/litert/next/npu](https://ai.google.dev/edge/litert/next/npu) for the instructions. 

Create CompiledModel for Inference with NPU

```kotlin
val env = Environment.create(BuiltinNpuAcceleratorProvider(context))

// For JIT models, create CompiledModel with model path directly
val jitCompiledModel = CompiledModel.create(
    "/path/to/my_jit_model.tflite",
    CompiledModel.Options(Accelerator.NPU),
    env,
)

// then run inference

```

# Details for Leverage Google AI Edge Gallery App  {#details-for-leverage-google-ai-edge-gallery-app}

Users can clone Google AI Edge and build their app based on the following instructions.

**Clone the Google AI Edge to build your own app**  
Detailed documentation can be found in the [LiteRT-LM Kotlin README](https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/README.md), but you can also start by [cloning the Google AI Edge Gallery app code](https://github.com/google-ai-edge/gallery) that’s built on top of LiteRT-LM Kotlin APIs.

**Step 1:** Clone the Repository  
First, grab the source code from GitHub. Open your terminal and run the following command:

```shell
git clone git@github.com:google-ai-edge/gallery.git
```

This will create a local copy of the repository in a directory named gallery.

**Step 2:** Navigate to the Android Source  
The Android project is located within the Android/src/ directory of the repository. Change your current directory to this location:

```shell
cd gallery/Android/src/
```

**Step 3**: Build and Install  
Now, we'll use the Gradle wrapper to build the debug version of the app and install it directly onto your connected device. Run:

```shell
./gradlew installDebug
```

Gradle will take care of downloading dependencies, compiling the code, and deploying the APK. Once finished, you should see "Edge Gallery" appearing in your app drawer.

**Step 4**: Link to Gallery App Gemma1B CPU/GPU/NPU

[https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt\#L168](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt#L168) 

