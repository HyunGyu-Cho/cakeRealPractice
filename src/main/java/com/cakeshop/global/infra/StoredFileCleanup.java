package com.cakeshop.global.infra;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 트랜잭션 결과에 맞춰 저장된 파일을 정리한다.
 *
 * <p>파일 시스템은 트랜잭션에 참여하지 않는다. {@code @Transactional} 안에서 파일을 쓰고
 * 뒤이은 INSERT 가 실패하면 <b>DB 는 되돌아가지만 파일은 디스크에 남는다</b>. 반대로 교체 성공 시에는
 * 이전 파일을 지워야 한다. 이 두 가지를 커밋/롤백 시점에 맞춰 처리한다.
 *
 * <p>원래 chat·product·store 에만 있던 처리다. review·주문제작은 같은 구조로 파일을 쓰면서
 * 이 정리가 없어 롤백 시 고아 파일이 남았다. 다섯 도메인이 같은 구현을 공유하게 모았다.
 *
 * <p>정리 실패는 삼킨다(로그만) — 본 작업은 이미 끝났고, 파일 삭제 실패로 사용자 요청을
 * 되돌리는 것은 손해가 더 크다.
 */
@Component
public class StoredFileCleanup {

    private static final Logger log = LoggerFactory.getLogger(StoredFileCleanup.class);

    private final FileStorageClient fileStorageClient;

    public StoredFileCleanup(FileStorageClient fileStorageClient) {
        this.fileStorageClient = fileStorageClient;
    }

    /**
     * 파일을 교체할 때 — 커밋되면 이전 파일을, 롤백되면 새 파일을 지운다.
     *
     * @return 등록했으면 {@code true}. 트랜잭션 밖(프록시를 거치지 않는 단위 테스트 등)에서는
     *     롤백 신호가 없어 등록하지 않고 {@code false}를 돌려준다 — 호출처가 {@link #deleteNow}로
     *     직접 정리한다. 삭제 <b>시점</b> 판단(성공 후냐 실패 시냐)은 도메인에 남긴다.
     */
    public boolean registerReplace(String previousPath, String newPath) {
        return register(previousPath, newPath);
    }

    /** 새로 올린 파일 — 롤백되면 지운다. 지울 이전 파일은 없다. */
    public boolean registerRollbackDelete(String path) {
        return register(null, path);
    }

    /** 여러 장을 한 번에 올릴 때(후기·주문제작 참고 이미지). */
    public boolean registerRollbackDelete(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return false;
        }
        boolean registered = false;
        for (String path : paths) {
            registered |= registerRollbackDelete(path);
        }
        return registered;
    }

    /** 트랜잭션 동기화가 없을 때 호출처가 직접 정리하는 경로. 실패는 로그만 남긴다. */
    public void deleteNow(String path) {
        deleteQuietly(path);
    }

    public void deleteNow(List<String> paths) {
        if (paths != null) {
            paths.forEach(this::deleteQuietly);
        }
    }

    private boolean register(String onCommit, String onRollback) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                deleteQuietly(status == TransactionSynchronization.STATUS_COMMITTED
                    ? onCommit : onRollback);
            }
        });
        return true;
    }

    private void deleteQuietly(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            fileStorageClient.delete(path);
        } catch (RuntimeException exception) {
            log.warn("저장 파일 정리에 실패했습니다: {}", path, exception);
        }
    }
}
