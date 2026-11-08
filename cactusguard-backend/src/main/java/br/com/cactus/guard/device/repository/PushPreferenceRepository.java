package br.com.cactus.guard.device.repository;

import br.com.cactus.guard.device.entity.PushPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PushPreferenceRepository extends JpaRepository<PushPreference, UUID> {
}
