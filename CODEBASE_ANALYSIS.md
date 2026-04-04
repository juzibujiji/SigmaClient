# Codebase Analysis Report

## 1. ZXing Dependency Status

**File:** `/e/.sigma/SigmaClient/pom.xml`

**Result:** ❌ **NO existing ZXing dependency found**

The pom.xml file contains the following key dependencies:
- LWJGL (graphics library)
- ViaVersion, ViaBackwards, ViaRewind
- Discord RPC
- YouTube-DL
- Various Minecraft dependencies (authlib, brigadier, etc.)
- Netty, Guava, GSON, HTTP components

**Recommendation:** ZXing library needs to be added as a new dependency for QR code generation.

---

## 2. Existing QR Code Related Code

**Result:** ❌ **NO existing QR code implementation found**

Search results for patterns: `QR`, `zxing`, `qrcode` across all Java and XML files returned **NO matches** (besides unrelated results in noise generation code).

This is a fresh implementation opportunity.

---

## 3. ResourceRegistry.java - Font Declarations

**File:** `/e/.sigma/SigmaClient/src/main/java/com/mentalfrostbyte/jello/util/client/render/ResourceRegistry.java`

### Font Declarations Found:

#### Light Fonts (Helvetica Neue Light):
```java
public static final TrueTypeFont JelloLightFont12 = getFont(
    "com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf", 0, 12.0F);

public static final TrueTypeFont JelloLightFont14 = getFont(
    "com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf", 0, 14.0F);

public static final TrueTypeFont JelloLightFont18 = getFont(
    "com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf", 0, 18.0F);

public static final TrueTypeFont JelloLightFont20 = getFont(
    "com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf", 0, 20.0F);

public static final TrueTypeFont JelloLightFont25 = getFont(
    "com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf", 0, 25.0F);

public static final TrueTypeFont JelloLightFont40 = getFont(
    "com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf", 0, 40.0F);

public static final TrueTypeFont JelloLightFont50 = getFont(
    "com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf", 0, 50.0F);

public static final TrueTypeFont JelloLightFont28 = getFont(
    "com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf", 0, 28.0F);

public static final TrueTypeFont JelloLightFont24 = getFont(
    "com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf", 0, 24.0F);

public static final TrueTypeFont JelloLightFont36 = getFont(
    "com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf", 0, 36.0F);
```

#### Medium Fonts (Helvetica Neue Medium):
```java
public static final TrueTypeFont JelloMediumFont20 = getFont(
    "com/mentalfrostbyte/gui/resources/font/helvetica-neue medium.ttf", 0, 20.0F);

public static final TrueTypeFont JelloMediumFont25 = getFont(
    "com/mentalfrostbyte/gui/resources/font/helvetica-neue medium.ttf", 0, 25.0F);

public static final TrueTypeFont JelloMediumFont40 = getFont(
    "com/mentalfrostbyte/gui/resources/font/helvetica-neue medium.ttf", 0, 40.0F);

public static final TrueTypeFont JelloMediumFont50 = getFont(
    "com/mentalfrostbyte/gui/resources/font/helvetica-neue medium.ttf", 0, 50.0F);
```

#### Regular Fonts:
```java
public static final TrueTypeFont RegularFont20 = getFont(
    "com/mentalfrostbyte/gui/resources/font/regular.ttf", 0, 20.0F);

public static final TrueTypeFont RegularFont40 = getFont(
    "com/mentalfrostbyte/gui/resources/font/regular.ttf", 0, 40.0F);
```

#### Special Fonts:
```java
public static final com.mentalfrostbyte.jello.util.client.render.DefaultClientFont DefaultClientFont = 
    new DefaultClientFont(2);

public static final TrueTypeFont LyricsFont = createLyricsFont();
```

### Font Creation Methods:

```java
public static TrueTypeFont getFont(String fontPath, int style, float size) {
    try {
        InputStream fontFile = Resources.readInputStream(fontPath);
        Font font = Font.createFont(0, fontFile);
        font = font.deriveFont(style, size);
        return new TrueTypeFont(font, true);
    } catch (Exception ex) {
        return new TrueTypeFont(new Font("Arial", Font.PLAIN, (int) size),
            Client.getInstance().clientMode != ClientMode.CLASSIC);
    }
}

public static TrueTypeFont getFont2(String fontPath, int style, float size) {
    try {
        InputStream fontFile = Resources.readInputStream(fontPath);
        Font font = Font.createFont(0, fontFile);
        font = font.deriveFont(style, size);
        return new TrueTypeFont(font, (int) size);
    } catch (Exception ex) {
        return new TrueTypeFont(new Font("Arial", Font.PLAIN, (int) size), true);
    }
}
```

---

## 4. RenderUtil.drawString Method Signatures

**File:** `/e/.sigma/SigmaClient/src/main/java/com/mentalfrostbyte/jello/util/game/render/RenderUtil.java`

### Method Overloads:

#### Overload 1: Full signature with FontSizeAdjust
```java
public static void drawString(
    TrueTypeFont res, 
    float var1,           // x position
    float var2,           // y position
    String string, 
    int var4,             // color
    FontSizeAdjust var5,  // width adjustment
    FontSizeAdjust var6   // height adjustment
)
```
**Lines:** 641-643
**Calls:** `drawString(res, var1, var2, string, var4, var5, var6, false)`

#### Overload 2: Full implementation with all parameters
```java
public static void drawString(
    TrueTypeFont font, 
    float x,              // x position
    float y,              // y position
    String text, 
    int color, 
    FontSizeAdjust widthAdjust,  // Handles NEGATE_AND_DIVIDE_BY_2, WIDTH_NEGATE
    FontSizeAdjust heightAdjust,  // Handles NEGATE_AND_DIVIDE_BY_2, HEIGHT_NEGATE
    boolean var7          // shadow toggle
)
```
**Lines:** 645-710

**Key Features:**
- Color extraction using bit shifting: `(color >> 24 & 0xFF)` for alpha, etc.
- GL matrix transformations for scaling
- Dynamic font switching based on `GuiManager.scaleFactor`:
  - JelloLightFont20 → JelloLightFont40 (at 2.0 scale)
  - JelloLightFont25 → JelloLightFont50 (at 2.0 scale)
  - JelloLightFont12 → JelloLightFont24 (at 2.0 scale)
  - JelloLightFont14 → JelloLightFont28 (at 2.0 scale)
  - JelloLightFont18 → JelloLightFont36 (at 2.0 scale)
  - RegularFont20 → RegularFont40 (at 2.0 scale)
  - JelloMediumFont20 → JelloMediumFont40 (at 2.0 scale)
  - JelloMediumFont25 → JelloMediumFont50 (at 2.0 scale)
- Shadow support with offset (y + 2)
- Blend mode: GL11.glBlendFunc(770, 771)

#### Overload 3: Simple signature
```java
public static void drawString(
    TrueTypeFont font, 
    float x, 
    float y, 
    String text, 
    int color
)
```
**Lines:** 728-730
**Calls:** `drawString(font, x, y, text, color, FontSizeAdjust.field14488, FontSizeAdjust.field14489, false)`

---

## Summary

| Item | Status | Notes |
|------|--------|-------|
| ZXing Dependency | ❌ Not Found | Needs to be added to pom.xml |
| Existing QR Code | ❌ Not Found | Clean slate for implementation |
| Font Registry | ✅ Found | 14+ fonts available, including Light, Medium, and Regular variants |
| RenderUtil.drawString | ✅ Found | 3 overloaded methods, supports scaling, shadows, and color adjustments |

---

## Recommended Next Steps

1. **Add ZXing dependency to pom.xml** (com.google.zxing:core)
2. **Create QR code generation utility** leveraging existing RenderUtil.drawString
3. **Use available fonts** for rendering any text overlays on QR codes
4. **Respect scaling factors** (GuiManager.scaleFactor) for UI consistency
