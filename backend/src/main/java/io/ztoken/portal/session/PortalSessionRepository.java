package io.ztoken.portal.session;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PortalSessionRepository extends JpaRepository<PortalSession, String> {
}
