package com.example.profile.repository;

import com.example.profile.model.StudentProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProfileRepository extends JpaRepository<StudentProfile, String> {

    List<StudentProfile> findByFullNameContainingIgnoreCase(String fullName);

    List<StudentProfile> findByGenderIgnoreCase(String gender);

    long countByGenderIgnoreCase(String gender);

    long countByStatusIgnoreCase(String status);

    @Query("SELECT s FROM StudentProfile s WHERE " +
            "(:name IS NULL OR :name = '' OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :name, '%')) OR LOWER(s.studentId) LIKE LOWER(CONCAT('%', :name, '%'))) AND "
            +
            "(:gender IS NULL OR :gender = '' OR UPPER(s.gender) = UPPER(:gender)) AND " +
            "(:status IS NULL OR :status = '' OR UPPER(s.status) = UPPER(:status)) AND " +
            "(:major IS NULL OR :major = '' OR LOWER(s.major) LIKE LOWER(CONCAT('%', :major, '%')))")
    Page<StudentProfile> searchAndFilterProfiles(
            @Param("name") String name,
            @Param("gender") String gender,
            @Param("status") String status,
            @Param("major") String major,
            Pageable pageable);

    Page<StudentProfile> findByFullNameContainingIgnoreCase(String name, Pageable pageable);
}