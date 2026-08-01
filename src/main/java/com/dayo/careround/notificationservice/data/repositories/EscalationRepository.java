package com.dayo.careround.notificationservice.data.repositories;

import com.dayo.careround.notificationservice.data.models.Escalation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EscalationRepository extends JpaRepository<Escalation, UUID> {
}