package consulting.segieth.vfs.domain;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.imageio.ImageIO;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "image")
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public class ImageFile extends FileNode {

    private static final int THUMBNAIL_WIDTH  = 200;
    private static final int THUMBNAIL_HEIGHT = 150;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "thumbnail")
    private byte[] thumbnail;

    public byte[] getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(byte[] thumbnail) {
        this.thumbnail = thumbnail;
    }

    public boolean hasThumbnail() {
        return thumbnail != null && thumbnail.length > 0;
    }

    /** Erzeugt ein Thumbnail aus dem Originalbild und speichert es im Feld. */
    public void generateThumbnail() throws IOException {
        BufferedImage original = getBufferedImage();
        if (original == null) {
            System.err.println("Thumbnail: ImageIO konnte Bild nicht lesen: " + getAddress());
            return;
        }

        Image scaled = original.getScaledInstance(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, Image.SCALE_SMOOTH);
        BufferedImage thumb = new BufferedImage(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, BufferedImage.TYPE_INT_RGB);
        thumb.getGraphics().drawImage(scaled, 0, 0, null);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(thumb, "jpg", bos);
        this.thumbnail = bos.toByteArray();
    }

    public BufferedImage getBufferedImage() throws IOException {
        Path path = getPath();
        BufferedImage bufferedImage = ImageIO.read(path.toFile());
        if (bufferedImage == null) {
            return null;
        }
        return bufferedImage;
    }

    public Path getPath() {
        return Paths.get(getAddress());
    }

}
