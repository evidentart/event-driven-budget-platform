package com.sea.userservice.mapper;

import com.sea.userservice.dto.UpdateProfileRequest;
import com.sea.userservice.dto.UserResponse;
import com.sea.userservice.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber(),
                user.getActive()
        );
    }

    public void updateEntity(UpdateProfileRequest request, User user) {
        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getPhoneNumber() != null) user.setPhoneNumber(request.getPhoneNumber());
    }
}
