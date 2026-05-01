package com.vantage.filters

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
            rgb += uBrightness;
            rgb = (rgb - 0.5) * uContrast + 0.5;
            float gray = dot(rgb, vec3(0.299, 0.587, 0.114));
            rgb = mix(vec3(gray), rgb, uSaturation);
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
            gray = smoothstep(0.1, 0.9, gray);
            gl_FragColor = vec4(vec3(gray), color.a);
        }
    """

    const val VIVID_FRAG = ADJUSTMENT_HEADER + """
        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            vec3 rgb = applyAdjustments(color.rgb);
            float gray = dot(rgb, vec3(0.299, 0.587, 0.114));
            rgb = mix(vec3(gray), rgb, 1.4);
            rgb = (rgb - 0.5) * 1.1 + 0.5;
            gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), color.a);
        }
    """

    const val CINEMATIC_FRAG = ADJUSTMENT_HEADER + """
        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            vec3 rgb = applyAdjustments(color.rgb);
            float lum = dot(rgb, vec3(0.299, 0.587, 0.114));
            vec3 shadows = vec3(0.0, 0.05, 0.1);
            vec3 highlights = vec3(0.1, 0.05, 0.0);
            rgb += mix(shadows, highlights, lum);
            float gray = dot(rgb, vec3(0.299, 0.587, 0.114));
            rgb = mix(vec3(gray), rgb, 0.85);
            rgb = (rgb - 0.5) * 1.15 + 0.5;
            gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), color.a);
        }
    """

    const val VINTAGE_FRAG = ADJUSTMENT_HEADER + """
        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            vec3 rgb = applyAdjustments(color.rgb);
            rgb.r = min(rgb.r * 1.1 + 0.06, 1.0);
            rgb.g = rgb.g * 1.0 + 0.04;
            rgb.b = rgb.b * 0.85 + 0.08;
            float gray = dot(rgb, vec3(0.299, 0.587, 0.114));
            rgb = mix(vec3(gray), rgb, 0.8);
            rgb = max(rgb, 0.05);
            gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), color.a);
        }
    """

    const val MUTED_FRAG = ADJUSTMENT_HEADER + """
        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            vec3 rgb = applyAdjustments(color.rgb);
            float gray = dot(rgb, vec3(0.299, 0.587, 0.114));
            rgb = mix(vec3(gray), rgb, 0.6);
            rgb = (rgb - 0.5) * 0.9 + 0.5;
            rgb += 0.04;
            gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), color.a);
        }
    """

    const val FADE_FRAG = ADJUSTMENT_HEADER + """
        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            vec3 rgb = applyAdjustments(color.rgb);
            rgb = (rgb - 0.5) * 0.9 + 0.5;
            rgb = max(rgb, 0.08);
            rgb.r += 0.02;
            rgb.g += 0.01;
            gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), color.a);
        }
    """

    const val DRAMATIC_FRAG = ADJUSTMENT_HEADER + """
        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            vec3 rgb = applyAdjustments(color.rgb);
            float gray = dot(rgb, vec3(0.299, 0.587, 0.114));
            rgb = mix(vec3(gray), rgb, 0.8);
            rgb = smoothstep(0.05, 0.95, rgb);
            float lum = dot(rgb, vec3(0.299, 0.587, 0.114));
            rgb.b += (1.0 - lum) * 0.05;
            gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), color.a);
        }
    """

    const val SILVERTONE_FRAG = ADJUSTMENT_HEADER + """
        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            vec3 rgb = applyAdjustments(color.rgb);
            float gray = dot(rgb, vec3(0.299, 0.587, 0.114));
            rgb = mix(vec3(gray), rgb, 0.1);
            rgb.b = min(rgb.b + 0.02, 1.0);
            rgb = (rgb - 0.5) * 1.1 + 0.5;
            gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), color.a);
        }
    """

    const val MONO_FRAG = ADJUSTMENT_HEADER + """
        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            vec3 rgb = applyAdjustments(color.rgb);
            float gray = dot(rgb, vec3(0.299, 0.587, 0.114));
            gl_FragColor = vec4(vec3(gray), color.a);
        }
    """
}
