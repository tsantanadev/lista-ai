package com.listaai.infrastructure.adapter.output.persistence.repository

import com.listaai.infrastructure.adapter.output.persistence.entity.ListEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ListJpaRepository : JpaRepository<ListEntity, Long>