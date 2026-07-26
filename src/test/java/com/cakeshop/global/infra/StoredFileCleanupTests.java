package com.cakeshop.global.infra;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 파일 시스템은 트랜잭션에 참여하지 않는다. 커밋/롤백 결과에 맞춰 어느 파일을 지울지 고정한다.
 *
 * <p>이 처리가 없어 review·주문제작은 롤백 시 디스크에 고아 파일을 남기고 있었다.
 */
@ExtendWith(MockitoExtension.class)
class StoredFileCleanupTests {

    @Mock private FileStorageClient fileStorageClient;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private StoredFileCleanup cleanup() {
        return new StoredFileCleanup(fileStorageClient);
    }

    private void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(synchronization -> synchronization.afterCompletion(status));
    }

    @Test
    void 교체는_커밋시_이전파일_롤백시_새파일을_지운다() {
        TransactionSynchronizationManager.initSynchronization();
        cleanup().registerReplace("/uploads/old.jpg", "/uploads/new.jpg");

        // 등록 시점에는 아무것도 지우지 않는다.
        verifyNoInteractions(fileStorageClient);

        complete(TransactionSynchronization.STATUS_COMMITTED);
        verify(fileStorageClient).delete("/uploads/old.jpg");
        verify(fileStorageClient, never()).delete("/uploads/new.jpg");
    }

    @Test
    void 롤백되면_새로_올린_파일을_지운다() {
        TransactionSynchronizationManager.initSynchronization();
        cleanup().registerReplace("/uploads/old.jpg", "/uploads/new.jpg");

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(fileStorageClient).delete("/uploads/new.jpg");
        verify(fileStorageClient, never()).delete("/uploads/old.jpg");
    }

    @Test
    void 신규_여러장은_롤백시_전부_지운다() {
        TransactionSynchronizationManager.initSynchronization();
        cleanup().registerRollbackDelete(List.of("/uploads/a.jpg", "/uploads/b.jpg"));

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(fileStorageClient).delete("/uploads/a.jpg");
        verify(fileStorageClient).delete("/uploads/b.jpg");
    }

    @Test
    void 신규_업로드는_커밋되면_아무것도_지우지_않는다() {
        TransactionSynchronizationManager.initSynchronization();
        cleanup().registerRollbackDelete("/uploads/a.jpg");

        complete(TransactionSynchronization.STATUS_COMMITTED);
        verifyNoInteractions(fileStorageClient);
    }

    /** 트랜잭션 밖에서는 롤백 신호가 없다 — 등록하지 않고 false 를 돌려 호출처가 직접 정리하게 한다. */
    @Test
    void 트랜잭션이_없으면_등록하지_않는다() {
        StoredFileCleanup cleanup = cleanup();

        org.assertj.core.api.Assertions
            .assertThat(cleanup.registerReplace("/uploads/old.jpg", "/uploads/new.jpg")).isFalse();
        org.assertj.core.api.Assertions
            .assertThat(cleanup.registerRollbackDelete("/uploads/a.jpg")).isFalse();
        verifyNoInteractions(fileStorageClient);
    }

    /** 정리 실패로 본 작업을 되돌리지 않는다 — 이미 끝난 요청을 파일 삭제 실패로 깨뜨리면 손해가 크다. */
    @Test
    void 삭제_실패는_삼킨다() {
        doThrow(new RuntimeException("디스크 오류")).when(fileStorageClient).delete("/uploads/a.jpg");

        cleanup().deleteNow("/uploads/a.jpg");

        verify(fileStorageClient).delete("/uploads/a.jpg");
    }
}
