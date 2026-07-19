package consulting.segieth.vfs;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import consulting.segieth.vfs.domain.ImageFile;
import consulting.segieth.vfs.service.ImageService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class ImageServiceTest {

    @Autowired
    ImageService service;

    @Test
    public void countImages() {
    	List<ImageFile> images = service.findAll();
    	assertNotNull(images);
    	System.out.println(images.size());
    }
}
