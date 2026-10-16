package com.ngockhanh.clinic.catalog.domain.repository;

import com.ngockhanh.clinic.catalog.domain.aggregate.Department;
import java.util.Optional;
import java.util.UUID;

/** Write side (command) repository for Department aggregate. */
public interface DepartmentRepository {
  /** Persists a new department; throws on duplicate canonical code. */
  void save(Department department);

  /**
   * Updates mutable fields and increments row_version using WHERE id=? AND row_version=?.
   *
   * @return true if the update matched exactly one row; false indicates a concurrent modification
   */
  boolean update(Department department, long expectedVersion);

  /**
   * Loads a department by id, acquiring a row-level lock (SELECT ... FOR UPDATE). Used to guard
   * writes and dependency checks.
   */
  Optional<Department> findByIdForUpdate(UUID id);

  /** Loads a department by id without locking. For read-only lookups. */
  Optional<Department> findById(UUID id);

  /** Returns the number of active rooms that belong to the given department. */
  long countActiveRooms(UUID departmentId);

  /** Returns the number of active services whose performingDepartmentId matches. */
  long countActiveServices(UUID departmentId);
}
