package com.repairshop.saas.masterdata.controller;

import com.repairshop.saas.masterdata.dto.*;
import com.repairshop.saas.masterdata.entity.*;
import com.repairshop.saas.masterdata.repository.*;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/master")
public class MasterDataController {

    private final MasterBrandRepository brandRepo;
    private final MasterModelRepository modelRepo;
    private final MasterRamOptionRepository ramRepo;
    private final MasterStorageOptionRepository storageRepo;
    private final MasterRepairServiceRepository repairServiceRepo;

    public MasterDataController(MasterBrandRepository brandRepo, MasterModelRepository modelRepo,
                                 MasterRamOptionRepository ramRepo, MasterStorageOptionRepository storageRepo,
                                 MasterRepairServiceRepository repairServiceRepo) {
        this.brandRepo = brandRepo;
        this.modelRepo = modelRepo;
        this.ramRepo = ramRepo;
        this.storageRepo = storageRepo;
        this.repairServiceRepo = repairServiceRepo;
    }

    // ---- Brands ----
    @GetMapping("/brands")
    public ResponseEntity<List<MasterBrand>> getBrands() {
        return ResponseEntity.ok(brandRepo.findAll());
    }

    @PostMapping("/brands")
    public ResponseEntity<MasterBrand> createBrand(@RequestBody BrandRequest req) {
        MasterBrand e = MasterBrand.builder()
                .name(req.getName())
                .imageUrl(req.getImageUrl())
                .imageBase64(req.getImageBase64())
                .build();
        return ResponseEntity.ok(brandRepo.save(e));
    }

    @PutMapping("/brands/{id}")
    public ResponseEntity<MasterBrand> updateBrand(@PathVariable UUID id, @RequestBody BrandRequest req) {
        return brandRepo.findById(id)
                .map(e -> {
                    e.setName(req.getName());
                    e.setImageUrl(req.getImageUrl());
                    if (req.getImageBase64() != null) e.setImageBase64(req.getImageBase64());
                    return ResponseEntity.ok(brandRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/brands/{id}")
    public ResponseEntity<Void> deleteBrand(@PathVariable UUID id) {
        if (!brandRepo.existsById(id)) return ResponseEntity.notFound().build();
        brandRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Models ----
    @GetMapping("/brands/{brandId}/models")
    public ResponseEntity<List<MasterModel>> getModelsByBrand(@PathVariable UUID brandId) {
        return ResponseEntity.ok(modelRepo.findByBrandIdOrderByName(brandId));
    }

    @PostMapping("/models")
    public ResponseEntity<MasterModel> createModel(@RequestBody ModelRequest req) {
        MasterModel e = MasterModel.builder()
                .brandId(req.getBrandId())
                .name(req.getName())
                .imageUrl(req.getImageUrl())
                .imageBase64(req.getImageBase64())
                .category(req.getCategory())
                .build();
        return ResponseEntity.ok(modelRepo.save(e));
    }

    @PutMapping("/models/{id}")
    public ResponseEntity<MasterModel> updateModel(@PathVariable UUID id, @RequestBody ModelRequest req) {
        return modelRepo.findById(id)
                .map(e -> {
                    e.setBrandId(req.getBrandId());
                    e.setName(req.getName());
                    e.setImageUrl(req.getImageUrl());
                    if (req.getImageBase64() != null) e.setImageBase64(req.getImageBase64());
                    e.setCategory(req.getCategory());
                    return ResponseEntity.ok(modelRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/models/{id}")
    public ResponseEntity<Void> deleteModel(@PathVariable UUID id) {
        if (!modelRepo.existsById(id)) return ResponseEntity.notFound().build();
        modelRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- RAM options ----
    @GetMapping("/ram-options")
    public ResponseEntity<List<MasterRamOption>> getRamOptions() {
        return ResponseEntity.ok(ramRepo.findAll());
    }

    @PostMapping("/ram-options")
    public ResponseEntity<MasterRamOption> createRamOption(@RequestBody RamOptionRequest req) {
        MasterRamOption e = MasterRamOption.builder().valueGb(req.getValueGb()).label(req.getLabel()).build();
        return ResponseEntity.ok(ramRepo.save(e));
    }

    @PutMapping("/ram-options/{id}")
    public ResponseEntity<MasterRamOption> updateRamOption(@PathVariable UUID id, @RequestBody RamOptionRequest req) {
        return ramRepo.findById(id)
                .map(e -> {
                    e.setValueGb(req.getValueGb());
                    e.setLabel(req.getLabel());
                    return ResponseEntity.ok(ramRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/ram-options/{id}")
    public ResponseEntity<Void> deleteRamOption(@PathVariable UUID id) {
        if (!ramRepo.existsById(id)) return ResponseEntity.notFound().build();
        ramRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Storage options ----
    @GetMapping("/storage-options")
    public ResponseEntity<List<MasterStorageOption>> getStorageOptions() {
        return ResponseEntity.ok(storageRepo.findAll());
    }

    @PostMapping("/storage-options")
    public ResponseEntity<MasterStorageOption> createStorageOption(@RequestBody StorageOptionRequest req) {
        MasterStorageOption e = MasterStorageOption.builder().valueGb(req.getValueGb()).label(req.getLabel()).build();
        return ResponseEntity.ok(storageRepo.save(e));
    }

    @PutMapping("/storage-options/{id}")
    public ResponseEntity<MasterStorageOption> updateStorageOption(@PathVariable UUID id, @RequestBody StorageOptionRequest req) {
        return storageRepo.findById(id)
                .map(e -> {
                    e.setValueGb(req.getValueGb());
                    e.setLabel(req.getLabel());
                    return ResponseEntity.ok(storageRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/storage-options/{id}")
    public ResponseEntity<Void> deleteStorageOption(@PathVariable UUID id) {
        if (!storageRepo.existsById(id)) return ResponseEntity.notFound().build();
        storageRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Repair services ----
    @GetMapping("/repair-services")
    public ResponseEntity<List<MasterRepairService>> getRepairServices() {
        return ResponseEntity.ok(repairServiceRepo.findAll());
    }

    @PostMapping("/repair-services")
    public ResponseEntity<MasterRepairService> createRepairService(@RequestBody RepairServiceRequest req) {
        MasterRepairService e = MasterRepairService.builder()
                .code(req.getCode()).name(req.getName()).description(req.getDescription()).build();
        return ResponseEntity.ok(repairServiceRepo.save(e));
    }

    @PutMapping("/repair-services/{id}")
    public ResponseEntity<MasterRepairService> updateRepairService(@PathVariable UUID id, @RequestBody RepairServiceRequest req) {
        return repairServiceRepo.findById(id)
                .map(e -> {
                    e.setCode(req.getCode());
                    e.setName(req.getName());
                    e.setDescription(req.getDescription());
                    return ResponseEntity.ok(repairServiceRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/repair-services/{id}")
    public ResponseEntity<Void> deleteRepairService(@PathVariable UUID id) {
        if (!repairServiceRepo.existsById(id)) return ResponseEntity.notFound().build();
        repairServiceRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
