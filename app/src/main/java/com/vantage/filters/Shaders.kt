package com.vantage.filters

/**
 * GLSL shaders for real-time camera filters and adjustments.
 */
object Shaders {

    const val VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """

    // Common header for fragment shaders including manual adjustments
    private const val ADJUSTMENT_HEADER = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTexCoord;
        uniform samplerExternalOES uTexture;
        uniform float uBrightness;
        uniform float uContrast;
        uniform float uSaturation;
        uniform float uGamma;

        vec3 applyAdjustments(vec3 rgb) {
            // Brightness
            rgb += uBrightness;
            
            // Contrast
            rgb = (rgb - 0.5) * uContrast + 0.5;
            
            // Saturation
            float gray = dot(rgb, vec3(0.299, 0.587, 0.114));
            rgb = mix(vec3(gray), rgb, uSaturation);
            
            // Gamma
            rgb = pow(max(rgb, 0.0), vec3(1.0 / uGamma));
            
            return clamp(rgb, 0.0, 1.0);
        }
    """

    const val NATURAL_FRAG = ADJUSTMENT_HEADER + """
        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            gl_FragColor = vec4(applyAdjustments(color.rgb), color.a);
        }
    """

    const val WARM_FRAG = ADJUSTMENT_HEADER + """
        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            vec3 rgb = applyAdjustments(color.rgb);
            rgb.r = min(rgb.r * 1.2, 1.0);
            rgb.g = rgb.g * 1.1;
            rgb.b = rgb.b * 0.9;
            gl_FragColor = vec4(rgb, color.a);
        }
    """

    const val COOL_FRAG = ADJUSTMENT_HEADER + """
        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            vec3 rgb = applyAdjustments(color.rgb);
            rgb.r = rgb.r * 0.9;
            rgb.g = rgb.g * 1.1;
            rgb.b = min(rgb.b * 1.2, 1.0);
            gl_FragColor = vec4(rgb, color.a);
        }
    """

    const val NOIR_FRAG = ADJUSTMENT_HEADER + """
        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            vec3 rgb = applyAdjustments(color.rgb);
            float gray = dot(rgb, vec3(0.299, 0.587, 0.114));
            // High contrast Noir
            gray = smoothstep(0.1, 0.9, gray);
            gl_FragColor = vec4(vec3(gray), color.a);
        }
    """

    const val VIVID_FRAG = ADJUSTMENT_HEADER + """
        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            vec3 rgb = applyAdjustments(color.rgb);
            // Boost saturation and contrast for Vivid
            float gray = dot(rgb, vec3(0.299, 0.587, 0.114));
            rgb = mix(vec3(gray), rgb, 1.4);
            rgb = (rgb - 0.5) * 1.1 + 0.5;
            gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), color.a);
        }
    """

    // Stubs for remaining filters to be filled with specific logic
    const val CINEMATIC_FRAG = NATURAL_FRAG
    const val VINTAGE_FRAG = NATURAL_FRAG
    const val MUTED_FRAG = NATURAL_FRAG
    const val FADE_FRAG = NATURAL_FRAG
    const val DRAMATIC_FRAG = NATURAL_FRAG
    const val SILVERTONE_FRAG = NATURAL_FRAG
    const val MONO_FRAG = NOIR_FRAG
}
