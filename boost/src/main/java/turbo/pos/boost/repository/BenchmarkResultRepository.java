package turbo.pos.boost.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import turbo.pos.boost.entity.BenchmarkResult;

import java.util.List;

@Repository
public interface BenchmarkResultRepository extends CrudRepository<BenchmarkResult, Long> {
    List<BenchmarkResult> findAllByOrderByCreatedAtDesc();
}
