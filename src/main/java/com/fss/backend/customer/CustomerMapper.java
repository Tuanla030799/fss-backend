package com.fss.backend.customer;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface CustomerMapper {
    List<Customer> listCustomers(@Param("status") String status, @Param("keyword") String keyword, @Param("limit") int limit, @Param("offset") int offset);
    long countCustomers(@Param("status") String status, @Param("keyword") String keyword);
    Customer findCustomerById(@Param("id") UUID id);
    int countEmail(@Param("email") String email, @Param("excludeId") UUID excludeId);
    int countPhone(@Param("phone") String phone, @Param("excludeId") UUID excludeId);
    void insertCustomer(@Param("id") UUID id, @Param("fullName") String fullName, @Param("email") String email,
                        @Param("phone") String phone, @Param("status") String status);
    void updateCustomer(@Param("id") UUID id, @Param("fullName") String fullName, @Param("email") String email,
                        @Param("phone") String phone, @Param("status") String status);
    void softDeleteCustomer(@Param("id") UUID id);
}
