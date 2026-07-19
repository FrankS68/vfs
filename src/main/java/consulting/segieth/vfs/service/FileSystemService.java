package consulting.segieth.vfs.service;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;

import consulting.segieth.vfs.domain.FileNode;
import consulting.segieth.vfs.domain.FileNodeRepository;
import consulting.segieth.vfs.domain.FileSystem;
import consulting.segieth.vfs.domain.FileSystemRepository;
import consulting.segieth.vfs.domain.ImageFile;
import consulting.segieth.vfs.ui.view.FileSystemView;

@Service
public class FileSystemService {

    private final FileSystemRepository fileSystemRepository;
    private final FileNodeRepository fileNodeRepository;
    private final ImageService imageService;

    protected FileSystemService(
    		FileSystemRepository fileSystemRepository,
    		FileNodeRepository fileNodeRepository,
    		ImageService imageService) {
        this.fileSystemRepository = fileSystemRepository;
        this.fileNodeRepository = fileNodeRepository;
        this.imageService = imageService;
		List<FileSystem> fileSystems = list(Pageable.unpaged());
		if (fileSystems.size() == 0) {
            createSystem(null,"Public", "P:\\", "private cloud", LocalDate.now());
            createSystem(null,"Pictures", "C:\\Users\\frank\\Pictures", "my pictures", LocalDate.now());
        }
    }

	public void scanAllFileSystems(UI current, FileSystemView view) {
		List<FileSystem> fileSystems = fileSystemRepository.findAllByType("system");
		if (data.getRootItems().size() == 0)
		fileSystems.forEach(fileSystem -> 
		{
			try {
				scan(current,view,getOrCreateNode(fileSystem));
			} catch (NoSuchFileException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		);
	}

    @Transactional
    public void createSystem(FileSystemView view,String name, String address, String description, @Nullable LocalDate synchDate) {
    	Path path = Paths.get(address);
    	FileSystem fs = null;
    	if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
    		fs = create(name,name, "system", path.normalize().toString(), description, synchDate);
    	}
    	try {
    		
			scan(UI.getCurrent(),view, getOrCreateNode(fs));
		} catch (NoSuchFileException e) {
			Notification.show(e.getMessage(), 3000, Position.TOP_END);
		}
    }

    public FileNode getDirectory(String name, String system, String address, String description, @Nullable LocalDate synchDate) {
    	return fileNodeRepository.findBySystemAndAddress(system, address);
    }

    public FileNode getBySystemAndAddress(String system, String address) {
    	return fileNodeRepository.findBySystemAndAddress(system, address);
    }

    @Transactional
    public FileNode createDirectory(String name, String system, String address, String description, @Nullable LocalDate synchDate) {
    	Path path = Paths.get(address);
    	return create(name, system, "directory", path.normalize().toString(), description, synchDate);
    }

    @Transactional
    public FileNode createFile(String name, String system, String address, String description, @Nullable LocalDate synchDate) {
    	Path path = Paths.get(address);
    	FileNode fileNode = imageService.createImage(name, system, path.normalize().toString(), description, synchDate);
    	if (fileNode == null) {
    		fileNode = create(name, system, "file", path.normalize().toString(), description, synchDate);
    	}
		return fileNode;
    }

    @Transactional
    public FileSystem create(String name, String system, String type, String address, String description, @Nullable LocalDate synchDate) {
        if ("fail".equals(description)) {
            throw new RuntimeException("This is for testing the error handler");
        }
        var fileSystem = new FileSystem(system,address,name);
        fileSystem.setType(type);
        fileSystem.setDescription(description);
        fileSystem.setSynchDate(LocalDate.now());
        fileSystemRepository.saveAndFlush(fileSystem);
        return fileSystem;
    }

    @Transactional
    public void save(Long id, String name, String system, String address, String description, String trashPath, @Nullable LocalDate synchDate) {
        if ("fail".equals(description)) {
            throw new RuntimeException("This is for testing the error handler");
        }
        var fileSystem = fileSystemRepository.findById(id).orElse(new FileSystem(system, address, name));
        fileSystem.setName(name);
        fileSystem.setAddress(address);
        fileSystem.setDescription(description != null ? description : "");
        fileSystem.setTrashPath(trashPath != null && !trashPath.isBlank() ? trashPath : null);
        fileSystem.setSynchDate(LocalDate.now());
        if (fileSystem.getType() == null) fileSystem.setType("system");
        fileSystemRepository.saveAndFlush(fileSystem);
    }

    @Transactional
    public void delete(FileSystem fileSystem) {
        fileSystemRepository.delete(fileSystem);
    }

    @Transactional(readOnly = true)
    public List<FileSystem> list(Pageable pageable) {
        return fileSystemRepository.findAllBy(pageable).toList();
    }

    @Transactional(readOnly = true)
    public FileSystem getFileSystemByAddress(String address) {
        return fileSystemRepository.findByAddress(address);
    }
    
    @Transactional(readOnly = true)
    public FileSystem getFileSystemById(Long id) {
        return fileSystemRepository.findById(id).get();
    }
    
    @Async
    public void scan(UI ui,FileSystemView view,VFSNode parentNode) throws NoSuchFileException {
    	FileSystem fs = (FileSystem) parentNode.getFileNode();
    	try(Stream<Path> files = Files.list(Paths.get(fs.getAddress()))) {
    		scanForDirectories(fs, parentNode, files);
            String message = "Scan für "+fs.getAddress()+" erfolgreich abgeschlossen!";
    		if (ui != null) {
				ui.access(() -> {
					Notification.show(
	                		message,
	                		3000, Notification.Position.MIDDLE);
	            });
    		}else {
    			System.out.println(message);
    		}
    	}
    	catch(NoSuchFileException ex) {
    		throw ex;
    	}
    	catch(IOException|UncheckedIOException ex) {
    		ex.printStackTrace();
    	}
    	try(Stream<Path> files = Files.list(Paths.get(fs.getAddress()))) {
    		scanForFiles(fs, parentNode, files);
    	}
    	catch(NoSuchFileException ex) {
    		throw ex;
    	}
    	catch(IOException|UncheckedIOException ex) {
    		ex.printStackTrace();
    	}
		ui.access(() -> {
			view.refreshAll();
        });
    }

	private void scanForDirectories(FileSystem fs, VFSNode parentNode, Stream<Path> files) {
		Stream<Path> directories = files.filter(Files::isDirectory);
		directories.forEach(directory -> {
			FileNode fileSystem = getDirectory(
					directory.getFileName().toString(), 
					fs.getSystem(),
					directory.normalize().toString(), 
					"", LocalDate.now());
			if (fileSystem == null) {
				fileSystem = createDirectory(
						directory.getFileName().toString(), 
						fs.getSystem(),
						directory.normalize().toString(), 
						"", LocalDate.now());
			}
			VFSNode directoryNode = new VFSNode(fileSystem);
			addRelation(parentNode, directoryNode);
		});
	}

	private void scanForFiles(FileSystem fs, VFSNode parentNode, Stream<Path> files) {
		files.forEach(file -> {
			if (!Files.isDirectory(file)) {
				FileNode fileSystem = getBySystemAndAddress(fs.getSystem(), file.normalize().toString());
				if (fileSystem == null) {
					fileSystem = createFile(
							file.getFileName().toString(), 
							fs.getSystem(),
							file.normalize().toString(), 
							"", LocalDate.now());
					VFSNode vfsNode = null;
					if (fileSystem instanceof ImageFile) {
						vfsNode = new ImageNode( (ImageFile) fileSystem);
					}
					else {
						vfsNode = new VFSNode(fileSystem);
					}
					addRelation(parentNode, vfsNode);
				}
			}
		});
	}

	private VFSNode getOrCreateNode(FileNode fs) {
		VFSNode parentNode = VFSNode.getByFileNode(fs);
		if (parentNode == null) {
			parentNode = new VFSNode(fs);
			addRelation(null, parentNode);
		}
		return parentNode;
	}

	private void addRelation(VFSNode parentNode, VFSNode directoryNode) {
		data.addItem(parentNode, directoryNode);
	}
    
    public List<FileSystem> list() {
    	return fileSystemRepository.findAll();
    }

	private VFSNode getRootItem(FileNode fs) {
		for (VFSNode rootItem : data.getRootItems()) {
    		if (rootItem.getName().equals(fs.getAddress())) {
    			return rootItem;
    		}
		}
		VFSNode rootNode = VFSNode.getByFileNode(fs);
		if (rootNode == null) {
			rootNode = new VFSNode(fs);
			addRelation(null,rootNode);
		}
		return rootNode;
	}

    private TreeDataProvider<VFSNode> dataProvider;
	TreeData<VFSNode> data;
	public TreeDataProvider<VFSNode> getTreeDataProvider() {
		if (data == null) data = new TreeData<VFSNode>();
		if (dataProvider == null) dataProvider = new TreeDataProvider<VFSNode>(data);
		return dataProvider;
	}

	public void countExtension(FileNode fs,String extension) {
		
	}
	
	public Set<String> listFilesUsingDirectoryStream(String dir) throws IOException {
	    Set<String> fileSet = new HashSet<String>();
	    try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(dir))) {
	        for (Path path : stream) {
	            if (!Files.isDirectory(path)) {
	                fileSet.add(path.getFileName()
	                    .toString());
	            }
	        }
	    }
	    return fileSet;
	}

	public Set<String> getDirectoryNames(String dir) {
		return getFilteredFileNames(dir, FILE_FILTER_DIRECTORIES);
	}
	
    public static Predicate<File> FILE_FILTER_DIRECTORIES = new Predicate<File>() {
		@Override
		public boolean test(File file) {
			return file.isDirectory();
		}
    };

    public static Predicate<Path> PATH_FILTER_DIRECTORIES = new Predicate<Path>() {
		@Override
		public boolean test(Path path) {
			return path.toFile().isDirectory();
		}
    };

    public static Predicate<File> FILTER_PDF = new Predicate<File>() {
		@Override
		public boolean test(File file) {
			return file.getName().toLowerCase().endsWith(".pdf");
		}
    };

    public static Predicate<File> FILTER_IMAGES = new Predicate<File>() {
    	private ArrayList<String> fileextensions;
		private ArrayList<String> getFileextensions() {
			if (fileextensions == null) {
		    	String[] extensions = {".gif",".png",".jpg",".jpeg"};
		    	fileextensions = new ArrayList<String>();
	    		for(String extension : extensions){
	    			fileextensions.add(extension);
	    		}
			}
			return fileextensions;
		}
		@Override
		public boolean test(File file) {
			String filename = file.getName().toLowerCase();
			String fileextension = filename.lastIndexOf(".") == -1 ? "" : filename.substring(filename.lastIndexOf("."));
			return getFileextensions().contains(fileextension);
		}
    };


	public Set<String> getFilteredFileNames(String dir, Predicate<File> filter) {
		return Stream.of(new File(dir).listFiles())
	      .filter(filter )
	      .map(File::getName)
	      .collect(Collectors.toSet());
	}
	
	public Integer countDirectories(FileNode fs) {
		return getDirectoryNames(fs.getAddress()).size();
	}

	public void scan(UI current, FileSystemView view, FileSystem fileSystem) throws NoSuchFileException {
		scan(current,view,getOrCreateNode(fileSystem));
	}

	/** Synchroner Scan eines einzelnen Verzeichnisses (für Lazy Loading im Tree) */
	public void scanSync(FileNode node) throws NoSuchFileException {
		FileSystem fs;
		if (node instanceof FileSystem) {
			fs = (FileSystem) node;
		} else {
			fs = new FileSystem(node.getSystem(), node.getAddress(), node.getName());
			fs.setId(node.getId());
			fs.setType(node.getType());
			fs.setDescription(node.getDescription() != null ? node.getDescription() : "");
			fs.setSynchDate(node.getSynchDate() != null ? node.getSynchDate() : java.time.LocalDate.now());
		}
		try {
			try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(java.nio.file.Paths.get(node.getAddress()))) {
				scanForDirectoriesDbOnly(fs, files);
			}
			try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(java.nio.file.Paths.get(node.getAddress()))) {
				scanForFilesDbOnly(fs, files);
			}
		} catch (java.io.IOException e) {
			if (e instanceof NoSuchFileException) throw (NoSuchFileException) e;
			e.printStackTrace();
		}
	}

	private void scanForDirectoriesDbOnly(FileSystem fs, java.util.stream.Stream<java.nio.file.Path> files) {
		files.filter(java.nio.file.Files::isDirectory).forEach(directory -> {
			FileNode existing = getDirectory(
					directory.getFileName().toString(),
					fs.getSystem(),
					directory.normalize().toString(),
					"", java.time.LocalDate.now());
			if (existing == null) {
				createDirectory(
						directory.getFileName().toString(),
						fs.getSystem(),
						directory.normalize().toString(),
						"", java.time.LocalDate.now());
			}
		});
	}

	private void scanForFilesDbOnly(FileSystem fs, java.util.stream.Stream<java.nio.file.Path> files) {
		files.filter(f -> !java.nio.file.Files.isDirectory(f)).forEach(file -> {
			FileNode existing = getBySystemAndAddress(fs.getSystem(), file.normalize().toString());
			if (existing == null) {
				FileNode node = createFile(
						file.getFileName().toString(),
						fs.getSystem(),
						file.normalize().toString(),
						"", java.time.LocalDate.now());
				if (node != null) {
					try {
						java.nio.file.attribute.BasicFileAttributes attrs =
							java.nio.file.Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes.class);
						node.setFileSize(attrs.size());
						node.setCreatedDate(attrs.creationTime().toInstant()
							.atZone(java.time.ZoneId.systemDefault()).toLocalDate());
						node.setModifiedDate(attrs.lastModifiedTime().toInstant()
							.atZone(java.time.ZoneId.systemDefault()).toLocalDate());
						fileNodeRepository.save(node);
					} catch (Exception e) {
						// Dateiattribute nicht verfügbar — kein Problem
					}
				}
			}
		});
	}

	public void countDoublettes(FileNode system) {
		new RuntimeException("not yet implemented");
	}
}
