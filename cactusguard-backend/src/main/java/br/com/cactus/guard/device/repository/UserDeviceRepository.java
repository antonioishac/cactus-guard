package br.com.cactus.guard.device.repository;

import br.com.cactus.guard.device.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {

    Optional<UserDevice> findByToken(String token);

    @Query("select d from UserDevice d where d.revokedAt is null and d.user.active = true and d.user.id in :userIds")
    List<UserDevice> findActiveByUserIds(@Param("userIds") Collection<UUID> userIds);

    @Query("select distinct d from UserDevice d join d.user u join u.roles r "
            + "where d.revokedAt is null and u.active = true and r.name in :roles")
    List<UserDevice> findActiveByRoles(@Param("roles") Collection<String> roles);

    @Modifying
    @Query("delete from UserDevice d where d.token = :token")
    int deleteByTokenValue(@Param("token") String token);
}
