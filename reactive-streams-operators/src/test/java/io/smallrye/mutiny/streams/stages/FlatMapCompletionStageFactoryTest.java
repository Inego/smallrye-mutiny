package io.smallrye.mutiny.streams.stages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.After;
import org.junit.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.Subscriptions;

/**
 * Checks the behavior of the {@link FlatMapCompletionStageFactory}.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class FlatMapCompletionStageFactoryTest extends StageTestBase {

    private final FlatMapCompletionStageFactory factory = new FlatMapCompletionStageFactory();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService computation = Executors.newFixedThreadPool(4);

    @After
    public void cleanup() {
        executor.shutdown();
        computation.shutdown();
    }

    @Test
    public void create() throws ExecutionException, InterruptedException {
        Multi<Integer> publisher = Multi.createFrom().items(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
                .emitOn(executor);

        List<String> list = ReactiveStreams.fromPublisher(publisher)
                .filter(i -> i < 4)
                .flatMapCompletionStage(this::square)
                .flatMapCompletionStage(this::asString)
                .toList()
                .run().toCompletableFuture().get();

        assertThat(list).containsExactly("1", "4", "9");
    }

    private CompletionStage<Integer> square(int i) {
        CompletableFuture<Integer> cf = new CompletableFuture<>();
        executor.submit(() -> cf.complete(i * i));
        return cf;
    }

    private CompletionStage<String> asString(int i) {
        CompletableFuture<String> cf = new CompletableFuture<>();
        executor.submit(() -> cf.complete(Objects.toString(i)));
        return cf;
    }

    @Test(expected = NullPointerException.class)
    public void createWithoutStage() {
        factory.create(null, null);
    }

    @Test(expected = NullPointerException.class)
    public void createWithoutFunction() {
        factory.create(null, () -> null);
    }

    @Test
    public void testInjectingANullCompletionStage() {
        AtomicReference<Subscriber<? super String>> reference = new AtomicReference<>();
        Publisher<String> publisher = s -> {
            reference.set(s);
            s.onSubscribe(Subscriptions.empty());
        };

        CompletableFuture<List<String>> future = ReactiveStreams.fromPublisher(publisher)
                .flatMapCompletionStage(s -> (CompletionStage<String>) null)
                .toList()
                .run()
                .toCompletableFuture();

        reference.get().onNext("a");
        try {
            future.join();
            fail("exception expected");
        } catch (CompletionException e) {
            assertThat(e).hasCauseInstanceOf(NullPointerException.class);
        }
    }

    @Test(expected = NullPointerException.class)
    public void testInjectingANullItem() {
        AtomicReference<Subscriber<? super String>> reference = new AtomicReference<>();
        Publisher<String> publisher = s -> {
            reference.set(s);
            s.onSubscribe(Subscriptions.empty());
        };

        ReactiveStreams.fromPublisher(publisher)
                .flatMapCompletionStage(s -> (CompletionStage<String>) null)
                .toList()
                .run()
                .toCompletableFuture();

        reference.get().onNext(null);
    }

    @Test(expected = NullPointerException.class)
    public void flatMapCsStageShouldFailIfNullIsReturned() {
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        CompletionStage<List<Object>> result = this.infiniteStream()
                .onTerminate(() -> cancelled.complete(null))
                .flatMapCompletionStage(t -> CompletableFuture.completedFuture(null)).toList().run();
        this.awaitCompletion(cancelled);
        this.awaitCompletion(result);
    }

}
