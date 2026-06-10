package vn.cxn.apache_camel.service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.cxn.apache_camel.model.dto.SystemLog;
import vn.cxn.apache_camel.model.entity.SystemLogEntity;
import vn.cxn.apache_camel.repository.SystemLogRepository;

@Service
public class SystemLogService {

    private static final Logger log = LoggerFactory.getLogger(SystemLogService.class);
    private static final int MAX_LOGS = 200;

    private final SystemLogRepository systemLogRepository;
    private final ClusterNodeService clusterNodeService;

    public SystemLogService(
            SystemLogRepository systemLogRepository,
            @org.springframework.context.annotation.Lazy ClusterNodeService clusterNodeService) {
        this.systemLogRepository = systemLogRepository;
        this.clusterNodeService = clusterNodeService;
    }

    @PostConstruct
    public void init() {
        log.info(
                "SystemLogService initialized (DB-backed). Loaded {} log(s)",
                systemLogRepository.count());
    }

    /** Get logs, optionally filtered by type, sorted by timestamp descending. */
    public List<SystemLog> getLogs(String type) {
        List<SystemLogEntity> entities;
        if (type != null && !type.isBlank()) {
            entities = systemLogRepository.findByTypeIgnoreCaseOrderByTimestampDesc(type);
        } else {
            entities = systemLogRepository.findAllByOrderByTimestampDesc();
        }
        return entities.stream().map(this::toDto).collect(Collectors.toList());
    }

    /** Log a new system action. Persists directly to DB and trims old logs if over MAX_LOGS. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(
            String type,
            String status,
            String target,
            String message,
            Integer version,
            String versionId,
            String fileName) {
        String instanceId = null;
        try {
            if (clusterNodeService != null) {
                instanceId = clusterNodeService.getInstanceId();
            }
        } catch (Exception ignored) {
        }
        log(type, status, target, message, version, versionId, fileName, instanceId);
    }

    /** Log a new system action with explicit instance ID. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(
            String type,
            String status,
            String target,
            String message,
            Integer version,
            String versionId,
            String fileName,
            String instanceId) {
        SystemLogEntity entity = new SystemLogEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setType(type);
        entity.setStatus(status);
        entity.setTarget(target);
        entity.setMessage(message);
        entity.setVersion(version);
        entity.setVersionId(versionId);
        entity.setFileName(fileName);
        entity.setInstanceId(instanceId);
        Instant now = Instant.now();
        entity.setTimestamp(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        systemLogRepository.save(entity);
        trimLogs();
    }

    /** Clear logs of a specific type (or all logs if type is null). */
    @Transactional
    public void clearLogs(String type) {
        if (type != null && !type.isBlank()) {
            systemLogRepository.deleteByTypeIgnoreCase(type);
        } else {
            systemLogRepository.deleteAll();
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /** Keep only the latest MAX_LOGS entries in the database. */
    @Transactional
    protected void trimLogs() {
        long total = systemLogRepository.count();
        if (total <= MAX_LOGS) return;

        // Find IDs of logs beyond the MAX_LOGS limit (oldest first)
        List<SystemLogEntity> keep =
                systemLogRepository.findTopNByOrderByTimestampDesc(PageRequest.of(0, MAX_LOGS));
        List<String> keepIds =
                keep.stream().map(SystemLogEntity::getId).collect(Collectors.toList());
        if (!keepIds.isEmpty()) {
            systemLogRepository.deleteByIdNotIn(keepIds);
        }
    }

    private SystemLog toDto(SystemLogEntity entity) {
        SystemLog dto =
                new SystemLog(
                        entity.getId(),
                        entity.getType(),
                        entity.getStatus(),
                        entity.getTarget(),
                        entity.getMessage(),
                        entity.getVersion(),
                        entity.getVersionId(),
                        entity.getFileName(),
                        entity.getInstanceId());
        dto.setTimestamp(entity.getTimestamp());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
