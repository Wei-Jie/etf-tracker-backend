package com.etftracker.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "ASSET_INFO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssetInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "asset_info_seq")
    @SequenceGenerator(name = "asset_info_seq", sequenceName = "asset_info_seq", allocationSize = 50)
    private Long assetId;

    @Column(nullable = false, unique = true)
    private String ticker;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String assetType;
}
