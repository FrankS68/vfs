package consulting.segieth.vfs;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import consulting.segieth.vfs.domain.FileNode;
import consulting.segieth.vfs.service.FileSystemService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
class FileServiceTest {

    @Autowired
    FileSystemService service;

    @Test
    public void countDirectoriesInP() {
    	FileNode system = service.getFileSystemByAddress("P:\\");
    	assertNotNull(system);
		service.countDirectories(system );
		assertNotEquals(0, service.countDirectories(system ));
    }

    @Test
    public void countDoublettesInP() {
    	FileNode system = service.getFileSystemByAddress("P:\\");
    	assertNotNull(system);
		service.countDoublettes(system );
		assertNotEquals(0, service.countDirectories(system ));
    }
}
