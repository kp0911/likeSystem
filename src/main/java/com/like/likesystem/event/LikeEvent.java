package com.like.likesystem.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LikeEvent implements Serializable {
    private Long videoId;
    private String userId;
    private String action;
}
