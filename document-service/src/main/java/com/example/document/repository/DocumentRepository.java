package com.example.document.repository;

import com.example.document.model.StudentDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<StudentDocument, Long> {
    List<StudentDocument> findByStudentIdIgnoreCase(String studentId);
}
