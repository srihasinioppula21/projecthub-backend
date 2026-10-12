package com.projecthub.repository;

import com.projecthub.model.Project;
import com.projecthub.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Query("select distinct p from Project p left join p.members m " +
           "where p.owner = :user or m = :user order by p.createdAt desc")
    List<Project> findForUser(@Param("user") User user);

    List<Project> findAllByOrderByCreatedAtDesc();
}
