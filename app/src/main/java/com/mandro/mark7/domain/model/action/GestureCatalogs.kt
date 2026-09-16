package com.mandro.mark7.domain.model.action

import com.mandro.mark7.domain.model.hand.HandDof

object GestureCatalogs {

    private val MIDDLE_FINGER_IMAGE_EXTENSION = mapOf(Gestures.MIDDLE_FINGER.id to "png")

    val DOF_5 = GestureCatalog(
        assetFolder = "mset_hand_shape/5dof",
        imageExtension = "jpg",
        imageExtensionOverrides = MIDDLE_FINGER_IMAGE_EXTENSION,
        groups = with(Gestures) {
            listOf(
                pair(CYLINDER_GRIP_OPEN, CYLINDER_GRIP_CLOSED),   // 1, 2
                pair(TIP_PINCH_OPEN, TIP_PINCH_CLOSED),           // 3, 4
                pair(LATERAL_PINCH_OPEN, LATERAL_PINCH_CLOSED),   // 5, 6
                pair(TRIPOD_OPEN, TRIPOD_CLOSED),                 // 7, 8
                pair(TRIGGER_OPEN, TRIGGER_CLOSED),               // 9, 10
                single(POINTING),                                 // 11
                single(PHONE),                                    // 12
                single(PEACE),                                    // 13
                single(THREE_FINGER_SALUTE),                      // 14
                single(FIST),                                     // 15
                single(MIDDLE_FINGER),                            // 16
                single(ELEVEN),                                   // 17
                single(SCISSOR),                                  // 18
                single(ANGLE),                                    // 19
            )
        },
    )

    val DOF_6 = GestureCatalog(
        assetFolder = "mset_hand_shape/6dof",
        imageExtension = "jpg",
        imageExtensionOverrides = MIDDLE_FINGER_IMAGE_EXTENSION,
        groups = with(Gestures) {
            listOf(
                pair(CYLINDER_GRIP_OPEN, CYLINDER_GRIP_CLOSED),   // 1, 2
                pair(TIP_PINCH_OPEN, TIP_PINCH_CLOSED),           // 3, 4
                pair(LATERAL_PINCH_OPEN, LATERAL_PINCH_CLOSED),   // 5, 6
                pair(TRIPOD_OPEN, TRIPOD_CLOSED),                 // 7, 8
                pair(TRIGGER_OPEN, TRIGGER_CLOSED),               // 9, 10
                single(POINTING),                                 // 11
                single(PHONE),                                    // 12
                single(PEACE),                                    // 13
                single(THREE_FINGER_SALUTE),                      // 14
                single(FIST),                                     // 15
                single(MIDDLE_FINGER),                            // 16
                single(ELEVEN),                                   // 17
                single(SCISSOR),                                  // 18
                single(ANGLE),                                    // 19
            )
        },
    )

    val DOF_7 = GestureCatalog(
        assetFolder = "mset_hand_shape/7dof",
        imageExtension = "jpg",
        imageExtensionOverrides = MIDDLE_FINGER_IMAGE_EXTENSION,
        groups = with(Gestures) {
            listOf(
                pair(CYLINDER_GRIP_OPEN, CYLINDER_GRIP_CLOSED),   // 1, 2
                pair(TIP_PINCH_OPEN, TIP_PINCH_CLOSED),           // 3, 4
                pair(LATERAL_PINCH_OPEN, LATERAL_PINCH_CLOSED),   // 5, 6
                pair(TRIPOD_OPEN, TRIPOD_CLOSED),                 // 7, 8
                pair(TRIGGER_OPEN, TRIGGER_CLOSED),               // 9, 10
                pair(SCISSOR_GRIP_OPEN, SCISSOR_GRIP_CLOSE),       // 11, 12
                pair(THREE_FINGER_GRIP_OPEN, THREE_FINGER_GRIP_CLOSE), // 13, 14
                single(POINTING),                                 // 15
                single(PHONE),                                    // 16
                single(PEACE),                                    // 17
                single(THREE_FINGER_SALUTE),                      // 18
                single(FIST),                                     // 19
                single(MIDDLE_FINGER),                            // 20
                single(ELEVEN),                                   // 21
                single(SCISSOR),                                  // 22
                single(ANGLE),                                    // 23
                single(VICTORY),                                  // 24
            )
        },
    )

    fun forDof(dof: HandDof): GestureCatalog = when (dof) {
        HandDof.DOF_5 -> DOF_5
        HandDof.DOF_6 -> DOF_6
        HandDof.DOF_7 -> DOF_7
    }

    /** Pair 묶음: [front] 는 앞 노드(S1·S3…), [back] 은 뒤 노드(S2·S4…)에 함께 들어간다. */
    private fun pair(front: Gesture, back: Gesture) = GestureSlots(lead = front, companion = back)

    /** 단일 묶음: 앞 노드에만 들어가고 뒤 노드는 비활성이다. */
    private fun single(gesture: Gesture) = GestureSlots(lead = gesture, companion = null)
}
