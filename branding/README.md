# MzFit artwork

`MzFit-original.png` is the user-supplied 1254 × 1254 PNG from `C:/Users/zakfa/Downloads/MzFit`, copied without changing its bytes. SHA-256: `6AB1E537BBEB680529F70CD428E997982197431B237312327EEA9F73F7E1A4E4`.

Android launcher and splash assets are resized/padded copies of this artwork. The green/lime mark and its original dark background are preserved. No generated replacement artwork is used.

- `mzfit-store-icon-512.png`: 512 × 512 PNG prepared for store graphics.
- Android `res/drawable-nodpi/mzfit_launcher_foreground.png`: 432 × 432 image with the original artwork centered at 264 × 264, protecting the logo inside adaptive launcher masks.
- Android `res/drawable-nodpi/mzfit_splash_logo.png`: 864 × 864 image with artwork centered at 528 × 528; rendered as a 288 dp system splash icon.
- Density-specific fallback launcher PNGs: 48, 72, 96, 144 and 192 pixels.

The surrounding background is `#02021D`, sampled from the source image's top-left pixel. Android's native splash theme uses the same color and hands off to the regular app theme once the first app frame is ready. No artificial startup delay or separate splash activity is added. Cold/warm starts show the system splash; simply returning to an already visible activity does not replay it.

Implementation follows Android's [adaptive icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive) and [SplashScreen API](https://developer.android.com/reference/androidx/core/splashscreen/SplashScreen), using cached AndroidX core-splashscreen 1.0.1 for API 26+ compatibility. This change applies launcher/splash resources to Android; the shared app header also displays MzFit.

Android 0.6.1 was built, installed and cold-launched on the Samsung SM-S938U. The splash was checked visually on-device. Drawable wrappers scale to their assigned bounds rather than imposing fixed layer sizes, preventing the system splash mask from clipping the logo. Android lint has no errors; themed monochrome launcher icons are not included in this artwork update.
