package all.continuous.gfx;

import org.lwjgl.nuklear.*;
import org.lwjgl.stb.*;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.*;

import static all.continuous.IOUtil.*;
import static org.lwjgl.nuklear.Nuklear.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.stb.STBTruetype.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Created by Roel on 18-03-17.
 */
public class Font {
    private NkUserFont default_font = NkUserFont.create();
    private ByteBuffer ttf;

    public Font(String fontPath, int fontSize) throws IOException {
        this(fontPath, fontSize, 1024, 1024);
    }

    public Font(String fontPath, int fontSize, int texWidth, int texHeight) throws IOException {
        /*
         * Copyright LWJGL. All rights reserved.
         * License terms: https://www.lwjgl.org/license
         */
        ttf = ioResourceToByteBuffer(fontPath, 160 * 1024);

        int fontTexID = glGenTextures();

        STBTTFontinfo fontInfo = STBTTFontinfo.create();
        STBTTPackedchar.Buffer cdata = STBTTPackedchar.create(95);

        float scale;
        float descent;

        try ( MemoryStack stack = stackPush() ) {
            stbtt_InitFont(fontInfo, ttf);
            scale = stbtt_ScaleForPixelHeight(fontInfo, fontSize);

            IntBuffer d = stack.mallocInt(1);
            stbtt_GetFontVMetrics(fontInfo, null, d, null);
            descent = d.get(0) * scale;

            ByteBuffer bitmap = memAlloc(texWidth * texHeight);

            STBTTPackContext pc = STBTTPackContext.mallocStack(stack);
            stbtt_PackBegin(pc, bitmap, texWidth, texHeight, 0, 1, NULL);
            stbtt_PackSetOversampling(pc, 4, 4);
            stbtt_PackFontRange(pc, ttf, 0, fontSize, 32, cdata);
            stbtt_PackEnd(pc);

            // Convert R8 to RGBA8
            ByteBuffer texture = memAlloc(texWidth * texHeight * 4);
            for ( int i = 0; i < bitmap.capacity(); i++ )
                texture.putInt((bitmap.get(i) << 24) | 0x00FFFFFF);
            texture.flip();

            glBindTexture(GL_TEXTURE_2D, fontTexID);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, texWidth, texHeight, 0, GL_RGBA, GL_UNSIGNED_INT_8_8_8_8_REV, texture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

            memFree(texture);
            memFree(bitmap);
        }

        default_font
                .width((handle, h, text, len) -> {
                    float text_width = 0;
                    try ( MemoryStack stack = stackPush() ) {
                        IntBuffer unicode = stack.mallocInt(1);

                        int glyph_len = nnk_utf_decode(text, memAddress(unicode), len);
                        int text_len = glyph_len;

                        if ( glyph_len == 0 )
                            return 0;

                        IntBuffer advance = stack.mallocInt(1);
                        while ( text_len <= len && glyph_len != 0 ) {
                            if ( unicode.get(0) == NK_UTF_INVALID )
                                break;

                        /* query currently drawn glyph information */
                            stbtt_GetCodepointHMetrics(fontInfo, unicode.get(0), advance, null);
                            text_width += advance.get(0) * scale;

						/* offset next glyph */
                            glyph_len = nnk_utf_decode(text + text_len, memAddress(unicode), len - text_len);
                            text_len += glyph_len;
                        }
                    }
                    return text_width;
                })
                .height(fontSize)
                .query((handle, font_height, glyph, codepoint, next_codepoint) -> {
                    try ( MemoryStack stack = stackPush() ) {
                        FloatBuffer x = stack.floats(0.0f);
                        FloatBuffer y = stack.floats(0.0f);

                        STBTTAlignedQuad q = STBTTAlignedQuad.mallocStack(stack);
                        IntBuffer advance = stack.mallocInt(1);

                        stbtt_GetPackedQuad(cdata, texWidth, texHeight, codepoint - 32, x, y, q, false);
                        stbtt_GetCodepointHMetrics(fontInfo, codepoint, advance, null);

                        NkUserFontGlyph ufg = NkUserFontGlyph.create(glyph);

                        ufg.width(q.x1() - q.x0());
                        ufg.height(q.y1() - q.y0());
                        ufg.offset().set(q.x0(), q.y0() + (fontSize + descent));
                        ufg.xadvance(advance.get(0) * scale);
                        ufg.uv(0).set(q.s0(), q.t0());
                        ufg.uv(1).set(q.s1(), q.t1());
                    }
                })
                .texture().id(fontTexID);
    }

    public NkUserFont getNKFont() {
        return default_font;
    }
}
