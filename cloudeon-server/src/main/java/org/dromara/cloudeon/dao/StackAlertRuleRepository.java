package org.dromara.cloudeon.dao;

import org.dromara.cloudeon.entity.CommandTaskGroupEntity;
import org.dromara.cloudeon.entity.StackAlertRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StackAlertRuleRepository extends JpaRepository<StackAlertRuleEntity, Integer> {
    StackAlertRuleEntity findByRuleNameAndStackRoleName(String ruleName, String stackRoleName);
}