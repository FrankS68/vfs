package consulting.segieth.vfs.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import consulting.segieth.vfs.domain.ImageFile;
import consulting.segieth.vfs.domain.ImageRepository;

@Service
public class ImageService {
	ImageRepository repository;
	
	public ImageService(ImageRepository repository) {
		this.repository = repository;
	}
	
	public void analyseImage(ImageFile image) {
		image.getAddress();
	}

	public ImageFile createImage(
			String name, 
			String system, 
			String pathString, 
			String description,
			LocalDate synchDate) {
		Path path = Paths.get(pathString);
		try {
			BufferedImage bufferedImage = getBufferedImage(path);
			if (bufferedImage == null) return null;
			ImageFile image = new ImageFile();
			image.setName(name);
			image.setSystem(system);
			image.setAddress(pathString);
			image.setType("image");
			image.setDescription(getDescription(bufferedImage));
			image.setSynchDate(synchDate);
			image.generateThumbnail();
	        repository.saveAndFlush(image);
	        return image;
		}
		catch (Exception exc) {
			exc.printStackTrace();
		}
		return null;
	}

	private BufferedImage getBufferedImage(Path path) throws IOException {
		BufferedImage bufferedImage = ImageIO.read(path.toFile());
		if (bufferedImage == null) {
			return null;
		}
		return bufferedImage;
	}

	private String getDescription(BufferedImage bufferedImage) {
		return String.format(
				"width %s height %s",
				bufferedImage.getWidth(),
				bufferedImage.getHeight()
				);
	}

	public List<ImageFile> findAll() {
		return repository.findAll();
	}

	public Long count() {
		return repository.count();
	}

	public Slice<ImageFile> findImages(String query, Pageable pageable) {
		return repository.findByQuery(query == null ? "" : query, pageable);
	}

	public Slice<ImageFile> findByFolder(String folderAddress, Pageable pageable) {
		return repository.findByFolder(folderAddress, pageable);
	}

	public Slice<ImageFile> findByTag(Long tagId, Pageable pageable) {
		return repository.findByTagId(tagId, pageable);
	}

	public List<ImageFile> findByIds(List<Long> ids) {
		return repository.findAllById(ids);
	}

	public boolean hasImagesUnder(String folderAddress) {
		return repository.hasImagesUnder(folderAddress);
	}

	public Slice<ImageFile> findAll(Pageable nextPageable) {
		return repository.findAll(nextPageable);
	}

	/** Generiert Thumbnails für alle Bilder die noch keines haben. */
	@org.springframework.scheduling.annotation.Async
	public void generateMissingThumbnails(java.util.function.BiConsumer<Long, Long> progressCallback) {
		long total = repository.count();
		long done = 0;
		int page = 0;
		Slice<ImageFile> slice;
		do {
			slice = repository.findAllBy(PageRequest.of(page++, 50));
			for (ImageFile image : slice.getContent()) {
				if (!image.hasThumbnail()) {
					try {
						image.generateThumbnail();
						repository.save(image);
					} catch (Exception e) {
						System.err.println("Thumbnail fehlgeschlagen für: " + image.getAddress() + " - " + e.getMessage());
					}
				}
				done++;
				try {
					progressCallback.accept(done, total);
				} catch (Exception e) {
					// UI-Session weg, trotzdem weitermachen
				}
			}
		} while (slice.hasNext());
		System.out.println("Thumbnail-Generierung abgeschlossen.");
	}
}
