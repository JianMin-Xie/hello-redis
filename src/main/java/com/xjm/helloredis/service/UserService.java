package com.xjm.helloredis.service;

import com.xjm.helloredis.entity.User;

public interface UserService {

    User save(User user);

    void delete(int id);

    User get(Integer id);
}