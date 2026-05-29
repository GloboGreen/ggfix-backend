package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterColor;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterColorRepository extends JpaRepository<MasterColor, UUID> {

    List<MasterColor> findAllByOrderBySortOrderAscNameAsc();
}
