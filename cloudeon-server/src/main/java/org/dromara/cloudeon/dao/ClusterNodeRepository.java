package org.dromara.cloudeon.dao;

import org.dromara.cloudeon.entity.ClusterNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClusterNodeRepository extends JpaRepository<ClusterNodeEntity, Integer> {
    Integer countByIp(String ip);

    Integer countByClusterId(Integer clusterId);

    ClusterNodeEntity findByHostname(String hostname);
    public List<ClusterNodeEntity> findByClusterId(Integer clusterId);
}