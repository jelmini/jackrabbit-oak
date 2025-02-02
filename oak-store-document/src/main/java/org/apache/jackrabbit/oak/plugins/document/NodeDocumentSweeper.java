/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.plugins.document;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.apache.jackrabbit.oak.commons.TimeDurationFormatter;
import org.apache.jackrabbit.oak.plugins.document.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static org.apache.jackrabbit.guava.common.collect.Iterables.filter;
import static org.apache.jackrabbit.guava.common.collect.Iterables.partition;
import static org.apache.jackrabbit.guava.common.collect.Iterables.transform;
import static org.apache.jackrabbit.oak.plugins.document.NodeDocument.isDeletedEntry;
import static org.apache.jackrabbit.oak.plugins.document.NodeDocument.removeCommitRoot;
import static org.apache.jackrabbit.oak.plugins.document.NodeDocument.removeRevision;
import static org.apache.jackrabbit.oak.plugins.document.NodeDocument.setDeletedOnce;
import static org.apache.jackrabbit.oak.plugins.document.util.Utils.PROPERTY_OR_DELETED_OR_COMMITROOT_OR_REVISIONS;

/**
 * The {@code NodeDocumentSweeper} is responsible for removing uncommitted
 * changes from {@code NodeDocument}s for a given clusterId.
 * <p>
 * This class is not thread-safe.
 */
final class NodeDocumentSweeper {

    private static final Logger LOG = LoggerFactory.getLogger(NodeDocumentSweeper.class);

    private static final int INVALIDATE_BATCH_SIZE = 100;

    private static final long LOGINTERVALMS = TimeUnit.MINUTES.toMillis(1);

    /** holds the Predicate actually used in sweepOne. This is modifiable ONLY FOR TESTING PURPOSE */
    static Predicate<String> SWEEP_ONE_PREDICATE = PROPERTY_OR_DELETED_OR_COMMITROOT_OR_REVISIONS;

    private final RevisionContext context;

    private final int clusterId;

    private final RevisionVector headRevision;

    private final boolean sweepNewerThanHead;

    private Revision head;

    private long totalCount;
    private long lastCount;
    private long startOfScan;
    private long lastLog;

    /**
     * Creates a new sweeper for the given context. The sweeper is initialized
     * in the constructor with the head revision provided by the revision
     * context. This is the head revision used later when the documents are
     * check for uncommitted changes in
     * {@link #sweep(Iterable, NodeDocumentSweepListener)}.
     * <p>
     * In combination with {@code sweepNewerThanHead == false}, the revision
     * context may return a head revision that is not up-to-date, as long as it
     * is consistent with documents passed to the {@code sweep()} method. That
     * is, the documents must reflect all changes visible from the provided head
     * revision. The sweeper will then only revert uncommitted changes up to the
     * head revision. With {@code sweepNewerThanHead == true}, the sweeper will
     * also revert uncommitted changes that are newer than the head revision.
     * This is usually only useful during recovery of a cluster node, when it is
     * guaranteed that there are no in-progress commits newer than the current
     * head revision.
     *
     * @param context the revision context.
     * @param sweepNewerThanHead whether uncommitted changes newer than the head
     *                 revision should be reverted.
     */
    NodeDocumentSweeper(RevisionContext context,
                        boolean sweepNewerThanHead) {
        this.context = requireNonNull(context);
        this.clusterId = context.getClusterId();
        this.headRevision= context.getHeadRevision();
        this.sweepNewerThanHead = sweepNewerThanHead;
    }

    /**
     * Performs a sweep and reports the required updates to the given sweep
     * listener. The returned revision is the new sweep revision for the
     * clusterId associated with the revision context used to create this
     * sweeper. The caller is responsible for storing the returned sweep
     * revision on the root document. This method returns {@code null} if no
     * update was possible.
     *
     * @param documents the documents to sweep
     * @param listener the listener to receive required sweep update operations.
     * @return the new sweep revision or {@code null} if no updates were done.
     * @throws DocumentStoreException if reading from the store or writing to
     *          the store failed.
     */
    @Nullable
    Revision sweep(@NotNull Iterable<NodeDocument> documents,
                   @NotNull NodeDocumentSweepListener listener)
            throws DocumentStoreException {
        return performSweep(documents, requireNonNull(listener));
    }

    /**
     * @return the head revision vector in use by this sweeper.
     */
    RevisionVector getHeadRevision() {
        return headRevision;
    }

    //----------------------------< internal >----------------------------------

    @Nullable
    private Revision performSweep(Iterable<NodeDocument> documents,
                                  NodeDocumentSweepListener listener)
            throws DocumentStoreException {
        head = headRevision.getRevision(clusterId);
        totalCount = 0;
        lastCount = 0;
        startOfScan = context.getClock().getTime();
        lastLog = startOfScan;

        if (head == null) {
            LOG.warn("Head revision does not have an entry for " +
                            "clusterId {}. Sweeping of documents is skipped.",
                    clusterId);
            return null;
        }

        Iterable<Map.Entry<Path, UpdateOp>> ops = sweepOperations(documents);
        for (List<Map.Entry<Path, UpdateOp>> batch : partition(ops, INVALIDATE_BATCH_SIZE)) {
            Map<Path, UpdateOp> updates = new HashMap<>();
            for (Map.Entry<Path, UpdateOp> entry : batch) {
                updates.put(entry.getKey(), entry.getValue());
            }
            listener.sweepUpdate(updates);
        }
        LOG.debug("Document sweep finished");
        return head;
    }

    private Iterable<Map.Entry<Path, UpdateOp>> sweepOperations(
            final Iterable<NodeDocument> docs) {
        return filter(transform(docs, doc -> new SimpleImmutableEntry<>(doc.getPath(), sweepOne(doc))),
                input -> input.getValue() != null);
    }

    private UpdateOp sweepOne(NodeDocument doc) throws DocumentStoreException {
        UpdateOp op = createUpdateOp(doc);
        // go through PROPERTY_OR_DELETED_OR_COMMITROOT_OR_REVISIONS, whereas :
        // - PROPERTY : for content changes
        // - DELETED : for new node (this)
        // - COMMITROOT : for new child (parent)
        // - REVISIONS : for commit roots (root for branch commits)
        for (String property : filter(doc.keySet(), SWEEP_ONE_PREDICATE::test)) {
            Map<Revision, String> valueMap = doc.getLocalMap(property);
            for (Map.Entry<Revision, String> entry : valueMap.entrySet()) {
                Revision rev = entry.getKey();
                // only consider change for this cluster node
                if (rev.getClusterId() != clusterId) {
                    continue;
                }
                Revision cRev = getCommitRevision(doc, rev);
                if (cRev == null) {
                    uncommitted(doc, property, rev, op);
                } else if (cRev.equals(rev)) {
                    committed(property, rev, op);
                } else {
                    committedBranch(doc, property, rev, cRev, op);
                }
            }
        }

        totalCount++;
        lastCount++;
        long now = context.getClock().getTime();
        long lastElapsed = now - lastLog;

        if (lastElapsed >= LOGINTERVALMS) {
            TimeDurationFormatter df = TimeDurationFormatter.forLogging();

            long totalElapsed = now - startOfScan;
            long totalRateMin = (totalCount * TimeUnit.MINUTES.toMillis(1)) / totalElapsed;
            long lastRateMin = (lastCount * TimeUnit.MINUTES.toMillis(1)) / lastElapsed;

            String message = String.format(
                    "Sweep on cluster node [%d]: %d nodes scanned in %s (~%d/m) - last interval %d nodes in %s (~%d/m)",
                    clusterId, totalCount, df.format(totalElapsed, TimeUnit.MILLISECONDS), totalRateMin, lastCount,
                    df.format(lastElapsed, TimeUnit.MILLISECONDS), lastRateMin);

            LOG.info(message);
            lastLog = now;
            lastCount = 0;
        }

        return op.hasChanges() ? op : null;
    }

    private void uncommitted(NodeDocument doc,
                             String property,
                             Revision rev,
                             UpdateOp op) {
        if (head.compareRevisionTime(rev) < 0 && !sweepNewerThanHead) {
            // ignore changes that happen after the
            // head we are currently looking at
            if (LOG.isDebugEnabled()) {
                LOG.debug("Uncommitted change on {}, {} @ {} newer than head {} ",
                        op.getId(), property, rev, head);
            }
            return;
        }
        if (isV18BranchCommit(rev, doc)) {
            // this is a not yet merged branch commit
            // -> do nothing
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unmerged branch commit on {}, {} @ {}",
                        op.getId(), property, rev);
            }
        } else {
            // this may be a not yet merged branch commit, but since it
            // wasn't created by this Oak version, it must be a left over
            // from an old branch which cannot be merged anyway.
            if (LOG.isDebugEnabled()) {
                LOG.debug("Uncommitted change on {}, {} @ {}",
                        op.getId(), property, rev);
            }
            op.removeMapEntry(property, rev);
            if (doc.getLocalCommitRoot().containsKey(rev)) {
                removeCommitRoot(op, rev);
            } else {
                removeRevision(op, rev);
            }
            // set _deletedOnce if uncommitted change is a failed create
            // node operation and doc does not have _deletedOnce yet
            if (isDeletedEntry(property)
                    && !doc.wasDeletedOnce()
                    && "false".equals(doc.getLocalDeleted().get(rev))) {
                setDeletedOnce(op);
            }
        }
    }

    /**
     * Returns {@code true} if the given revision is marked as a branch commit
     * on the document. This method only checks local branch commit information
     * available on the document ({@link NodeDocument#getLocalBranchCommits()}).
     * If the given revision is related to a branch commit that was created
     * prior to Oak 1.8, the method will return {@code false}.
     *
     * @param rev a revision.
     * @param doc the document to check.
     * @return {@code true} if the revision is marked as a branch commit;
     *          {@code false} otherwise.
     */
    private boolean isV18BranchCommit(Revision rev, NodeDocument doc) {
        return doc.getLocalBranchCommits().contains(rev);
    }

    private void committed(String property,
                           Revision rev,
                           UpdateOp op) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Committed change on {}, {} @ {}",
                    op.getId(), property, rev);
        }
    }

    private void committedBranch(NodeDocument doc,
                                 String property,
                                 Revision rev,
                                 Revision cRev,
                                 UpdateOp op) {
        boolean newerThanHead = cRev.compareRevisionTime(head) > 0;
        if (LOG.isDebugEnabled()) {
            String msg = newerThanHead ? " (newer than head)" : "";
            LOG.debug("Committed branch change on {}, {} @ {}/{}{}",
                    op.getId(), property, rev, cRev, msg);
        }
        if (!isV18BranchCommit(rev, doc)) {
            NodeDocument.setBranchCommit(op, rev);
        }
    }

    private static UpdateOp createUpdateOp(NodeDocument doc) {
        return new UpdateOp(doc.getId(), false);
    }

    @Nullable
    private Revision getCommitRevision(final NodeDocument doc,
                                       final Revision rev)
            throws DocumentStoreException {
        String cv = context.getCommitValue(rev, doc);
        if (cv == null) {
            return null;
        }
        return Utils.resolveCommitRevision(rev, cv);
    }
}
