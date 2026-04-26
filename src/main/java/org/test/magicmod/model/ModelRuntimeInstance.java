package org.test.magicmod.model;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class ModelRuntimeInstance {
    private String currentAnimation = "idle";
    private AnimationPlayMode playMode = AnimationPlayMode.DEFAULT;
    private float lastAgeInTicks;
    private float animationStartAgeInTicks;
    private boolean restartPending = true;
    private boolean stopped;
    private int allocatedBoneCount = -1;
    private Matrix4f[] localBoneTransforms = new Matrix4f[0];
    private Matrix4f[] globalBoneTransforms = new Matrix4f[0];
    private final Vector3f positionScratch = new Vector3f();
    private final Vector3f rotationScratch = new Vector3f();
    private final Vector3f scaleScratch = new Vector3f();

    public String currentAnimation() {
        return currentAnimation;
    }

    public void play(String animation) {
        play(animation, AnimationPlayMode.DEFAULT);
    }

    public void play(String animation, AnimationPlayMode nextMode) {
        if (animation == null || animation.isBlank()) {
            animation = "idle";
        }
        if (nextMode == null) {
            nextMode = AnimationPlayMode.DEFAULT;
        }
        boolean changed = !animation.equals(currentAnimation) || nextMode != playMode || stopped;
        currentAnimation = animation;
        playMode = nextMode;
        stopped = false;
        restartPending = changed;
    }

    public void ensureAnimation(String animation) {
        if (animation == null || animation.isBlank()) {
            animation = "idle";
        }
        if (!animation.equals(currentAnimation)) {
            play(animation, AnimationPlayMode.DEFAULT);
        }
    }

    public void stop() {
        stopped = true;
    }

    public void reset() {
        stopped = false;
        restartPending = true;
    }

    public void update(float ageInTicks) {
        lastAgeInTicks = ageInTicks;
    }

    public void update(RuntimeModel model, RuntimeAnimationClip clip, float ageInTicks) {
        update(ageInTicks);
        if (restartPending) {
            animationStartAgeInTicks = ageInTicks;
            restartPending = false;
        }
        updateBoneTransforms(model.skeleton(), clip);
    }

    public float animationTimeSeconds(RuntimeAnimationClip clip) {
        float seconds = Math.max(0.0f, (lastAgeInTicks - animationStartAgeInTicks) / 20.0f);
        if (!isLooping(clip)) {
            return Math.min(seconds, clip.lengthSeconds());
        }
        return seconds % clip.lengthSeconds();
    }

    public void transformPosition(RuntimeVertex vertex, Vector3f destination) {
        if (stopped || vertex.boneIndex() >= globalBoneTransforms.length) {
            destination.set(vertex.x(), vertex.y(), vertex.z());
            return;
        }
        globalBoneTransforms[vertex.boneIndex()].transformPosition(vertex.x(), vertex.y(), vertex.z(), destination);
    }

    public void transformNormal(RuntimeVertex vertex, Vector3f destination) {
        if (stopped || vertex.boneIndex() >= globalBoneTransforms.length) {
            destination.set(vertex.normalX(), vertex.normalY(), vertex.normalZ());
            return;
        }
        globalBoneTransforms[vertex.boneIndex()]
            .transformDirection(vertex.normalX(), vertex.normalY(), vertex.normalZ(), destination)
            .normalize();
    }

    private boolean isLooping(RuntimeAnimationClip clip) {
        if (playMode == AnimationPlayMode.LOOP) {
            return true;
        }
        if (playMode == AnimationPlayMode.ONCE) {
            return false;
        }
        return clip.loop();
    }

    private void updateBoneTransforms(RuntimeSkeleton skeleton, RuntimeAnimationClip clip) {
        int boneCount = skeleton.boneCount();
        ensureBoneCapacity(boneCount);
        if (boneCount == 0) {
            return;
        }
        if (stopped || !clip.hasBoneAnimations()) {
            for (int index = 0; index < boneCount; index++) {
                globalBoneTransforms[index].identity();
            }
            return;
        }

        float timeSeconds = animationTimeSeconds(clip);
        for (int index = 0; index < boneCount; index++) {
            RuntimeBone bone = skeleton.bone(index);
            RuntimeBoneAnimation animation = clip.boneAnimation(bone.name());
            animation.samplePosition(timeSeconds, positionScratch);
            animation.sampleRotation(timeSeconds, rotationScratch);
            animation.sampleScale(timeSeconds, scaleScratch);

            Matrix4f local = localBoneTransforms[index].identity()
                .translate(
                    bone.pivotX() + positionScratch.x(),
                    bone.pivotY() + positionScratch.y(),
                    bone.pivotZ() + positionScratch.z()
                )
                .rotateXYZ(rotationScratch.x(), rotationScratch.y(), rotationScratch.z())
                .scale(scaleScratch.x(), scaleScratch.y(), scaleScratch.z())
                .translate(-bone.pivotX(), -bone.pivotY(), -bone.pivotZ());

            int parentIndex = bone.parentIndex();
            if (parentIndex >= 0 && parentIndex < index) {
                globalBoneTransforms[index].set(globalBoneTransforms[parentIndex]).mul(local);
            } else {
                globalBoneTransforms[index].set(local);
            }
        }
    }

    private void ensureBoneCapacity(int boneCount) {
        if (allocatedBoneCount == boneCount) {
            return;
        }
        localBoneTransforms = new Matrix4f[boneCount];
        globalBoneTransforms = new Matrix4f[boneCount];
        for (int index = 0; index < boneCount; index++) {
            localBoneTransforms[index] = new Matrix4f();
            globalBoneTransforms[index] = new Matrix4f();
        }
        allocatedBoneCount = boneCount;
    }
}
