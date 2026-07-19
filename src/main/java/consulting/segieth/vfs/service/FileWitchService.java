package consulting.segieth.vfs.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.stereotype.Service;

@Service
public class FileWitchService {
	public void scanDirectory(String path) {
		scanDirectory(Paths.get(path));
	}

	public void scanDirectory(Path path) {
		Files.exists(path);
		Files.isDirectory(path);
	}
}
