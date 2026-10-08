# Spec: User Authentication and Role Permissions (user-auth)

## Purpose
提供原料中心评审管理系统的用户身份认证与基于 RBAC 的多角色权限控制。支持单账号兼任多个角色并在系统顶部自由切换工作台身份（系统管理员 ADMIN、中心领导 LEADER、评审专家 EXPERT、被考核员工 EMPLOYEE），并为管理员提供账号全生命周期管理（创建、编辑、状态启停）。

## Requirements

### Requirement: User authentication and session management
The system SHALL support user login via username and password, issue a secure token upon successful authentication, and allow users to log out.

#### Scenario: Successful login
- **WHEN** user provides valid username and password
- **THEN** system authenticates user, returns a token and user profile including assigned roles

#### Scenario: Invalid login credentials
- **WHEN** user provides an incorrect username or password
- **THEN** system denies access and returns an authentication error message

### Requirement: Role-Based Access Control and multi-role switching
The system SHALL support RBAC roles (ADMIN, LEADER, EXPERT, EMPLOYEE), allow a user to hold multiple roles, and allow the user to switch their active working role during a session.

#### Scenario: User switches active role
- **WHEN** a user with multiple roles selects a different role from the top navigation role switcher
- **THEN** system updates the current session role and dynamically filters accessible menus and functional permissions

### Requirement: User account management
The system SHALL allow administrators to view, create, update, and enable/disable user accounts.

#### Scenario: Administrator creates new user
- **WHEN** administrator submits username, real name, department, and assigned roles
- **THEN** system saves the new user record and enables access
