package com.cloudzone.shopbackend.catalog;

import com.cloudzone.shopbackend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A product brand. Products reference it optionally, like {@link Category}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "brands")
public class Brand extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** URL-safe identifier, e.g. "aurora". */
    @Column(name = "slug", nullable = false, unique = true, length = 160)
    private String slug;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active;
}
