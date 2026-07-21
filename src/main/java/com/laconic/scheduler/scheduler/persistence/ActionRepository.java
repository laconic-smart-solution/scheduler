package com.laconic.scheduler.scheduler.persistence;

import com.laconic.scheduler.scheduler.domain.Action;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence port for {@link Action} (WHAT to execute).
 */
public interface ActionRepository extends JpaRepository<Action, String> {}
