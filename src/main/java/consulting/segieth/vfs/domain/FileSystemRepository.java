package consulting.segieth.vfs.domain;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FileSystemRepository extends JpaRepository<FileSystem, Long>, JpaSpecificationExecutor<FileSystem> {

    Slice<FileSystem> findAllBy(Pageable pageable);

    FileSystem findByAddress(String address);
    FileSystem findBySystemAndAddress(String system,String address);

	List<FileSystem> findAllByType(String string);

}
