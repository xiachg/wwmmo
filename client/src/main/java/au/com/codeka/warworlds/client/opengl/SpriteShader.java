package au.com.codeka.warworlds.client.opengl;

import android.opengl.GLES20;
import android.text.TextUtils;
import au.com.codeka.warworlds.common.Log;

/**
 * {@link SpriteShader} manages the shader program used to render a sprite.
 */
public class SpriteShader extends Shader {
  private static final Log log = new Log("SpriteShader");

  private int posHandle;
  private int texCoordHandle;
  private int texSamplerHandle;

  public SpriteShader() {
  }

  public int getPositionHandle() {
    return posHandle;
  }

  public int getTexCoordHandle() {
    return texCoordHandle;
  }

  @Override
  protected String getVertexShaderCode() {
    return TextUtils.join("\n", new String[]{
        "uniform mat4 uMvpMatrix;",
        "attribute vec4 aPosition;",
        "attribute vec2 aTexCoord;",
        "varying vec2 vTexCoord;",
        "void main() {",
        "  vTexCoord = aTexCoord;",
        "  gl_Position = uMvpMatrix * aPosition;",
        "}",
    });
  }

  @Override
  protected String getFragmentShaderCode() {
    return TextUtils.join("\n", new String[]{
        "precision mediump float;",
        "uniform sampler2D uTexture;",
        "varying vec2 vTexCoord;",
        "void main() {",
        "  gl_FragColor = texture2D(uTexture, vTexCoord);",
        "}",
    });
  }

  @Override
  protected void onCompile() {
    posHandle = getAttributeLocation("aPosition");
    texCoordHandle = getAttributeLocation("aTexCoord");
    texSamplerHandle = getUniformLocation("uTexture");
  }

  @Override
  protected void onBegin() {
    GLES20.glEnableVertexAttribArray(posHandle);
    GLES20.glEnableVertexAttribArray(texCoordHandle);
    GLES20.glUniform1i(texSamplerHandle , 0);
  }

  @Override
  protected void onEnd() {
    GLES20.glDisableVertexAttribArray(posHandle);
    GLES20.glDisableVertexAttribArray(texCoordHandle);
  }
}
