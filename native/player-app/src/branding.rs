use eframe::egui::{ColorImage, Context, TextureHandle, TextureOptions};

pub struct Branding {
    pub icon: TextureHandle,
    pub mascot: TextureHandle,
}

impl Branding {
    pub fn load(ctx: &Context) -> Result<Self, image::ImageError> {
        Ok(Self {
            icon: load_texture(
                ctx,
                "danmaku-app-icon",
                include_bytes!("../assets/app-icon.png"),
            )?,
            mascot: load_texture(
                ctx,
                "danmaku-app-mascot",
                include_bytes!("../assets/app-mascot.png"),
            )?,
        })
    }
}

fn load_texture(
    ctx: &Context,
    name: &str,
    bytes: &[u8],
) -> Result<TextureHandle, image::ImageError> {
    let rgba = image::load_from_memory(bytes)?.into_rgba8();
    let size = [rgba.width() as usize, rgba.height() as usize];
    let image = ColorImage::from_rgba_unmultiplied(size, rgba.as_raw());
    Ok(ctx.load_texture(name, image, TextureOptions::LINEAR))
}

#[cfg(test)]
mod tests {
    #[test]
    fn bundled_branding_images_decode_and_are_square() {
        for bytes in [
            include_bytes!("../assets/app-icon.png").as_slice(),
            include_bytes!("../assets/app-mascot.png").as_slice(),
        ] {
            let image = image::load_from_memory(bytes).expect("bundled branding image decodes");
            assert_eq!(image.width(), image.height());
            assert!(image.width() >= 512);
        }
    }
}
