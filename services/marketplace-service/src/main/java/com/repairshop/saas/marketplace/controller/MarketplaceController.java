package com.repairshop.saas.marketplace.controller;

import com.repairshop.saas.marketplace.dto.ProductRequest;
import com.repairshop.saas.marketplace.dto.ProductResponse;
import com.repairshop.saas.marketplace.entity.MarketplaceProduct;
import com.repairshop.saas.marketplace.exception.ResourceNotFoundException;
import com.repairshop.saas.marketplace.repository.MarketplaceProductRepository;
import com.repairshop.saas.marketplace.service.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/marketplace")
@RequiredArgsConstructor
public class MarketplaceController {

    private final MarketplaceProductRepository productRepo;

    @GetMapping("/products")
    public ResponseEntity<List<ProductResponse>> listProducts(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false, defaultValue = "ACTIVE") String status,
            @RequestParam(value = "modelId", required = false) UUID modelId,
            @RequestParam(value = "q", required = false) String q
    ) {
        List<MarketplaceProduct> result;
        if (type != null && !type.isBlank()) {
            result = productRepo.findByTypeAndStatus(type.toUpperCase(), status.toUpperCase());
        } else if (modelId != null) {
            result = productRepo.findByModelIdAndStatus(modelId, status.toUpperCase());
        } else {
            result = productRepo.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
        }
        if (q != null && !q.isBlank()) {
            String needle = q.toLowerCase();
            result = result.stream().filter(p ->
                    (p.getTitle() != null && p.getTitle().toLowerCase().contains(needle)) ||
                    (p.getDescription() != null && p.getDescription().toLowerCase().contains(needle))
            ).toList();
        }
        return ResponseEntity.ok(result.stream().map(ProductMapper::toResponse).toList());
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable UUID id) {
        MarketplaceProduct p = productRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        return ResponseEntity.ok(ProductMapper.toResponse(p));
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> createProduct(@RequestBody ProductRequest req) {
        MarketplaceProduct p = MarketplaceProduct.builder()
                .shopId(req.getShopId())
                .brandId(req.getBrandId())
                .modelId(req.getModelId())
                .title(req.getTitle())
                .description(req.getDescription())
                .type(req.getType() != null ? req.getType().toUpperCase() : "SELL")
                .price(req.getPrice())
                .status(req.getStatus() != null ? req.getStatus().toUpperCase() : "ACTIVE")
                .conditionLabel(req.getConditionLabel())
                .color(req.getColor())
                .storageLabel(req.getStorageLabel())
                .network(req.getNetwork())
                .imageUrl(req.getImageUrl())
                .extraImageUrls(ProductMapper.serializeExtraImages(req.getExtraImageUrls()))
                .build();
        return ResponseEntity.ok(ProductMapper.toResponse(productRepo.save(p)));
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable UUID id, @RequestBody ProductRequest req) {
        MarketplaceProduct p = productRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        if (req.getShopId() != null) p.setShopId(req.getShopId());
        if (req.getBrandId() != null) p.setBrandId(req.getBrandId());
        if (req.getModelId() != null) p.setModelId(req.getModelId());
        if (req.getTitle() != null) p.setTitle(req.getTitle());
        if (req.getDescription() != null) p.setDescription(req.getDescription());
        if (req.getType() != null) p.setType(req.getType().toUpperCase());
        if (req.getPrice() != null) p.setPrice(req.getPrice());
        if (req.getStatus() != null) p.setStatus(req.getStatus().toUpperCase());
        if (req.getConditionLabel() != null) p.setConditionLabel(req.getConditionLabel());
        if (req.getColor() != null) p.setColor(req.getColor());
        if (req.getStorageLabel() != null) p.setStorageLabel(req.getStorageLabel());
        if (req.getNetwork() != null) p.setNetwork(req.getNetwork());
        if (req.getImageUrl() != null) p.setImageUrl(req.getImageUrl());
        if (req.getExtraImageUrls() != null) p.setExtraImageUrls(ProductMapper.serializeExtraImages(req.getExtraImageUrls()));
        return ResponseEntity.ok(ProductMapper.toResponse(productRepo.save(p)));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        if (!productRepo.existsById(id)) return ResponseEntity.notFound().build();
        productRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
