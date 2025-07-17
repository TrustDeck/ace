/*
 * Trust Deck Services
 * Copyright 2022-2024 Armin Müller & Eric Wündisch
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.trustdeck.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.trustdeck.configuration.RoleConfig;
import org.trustdeck.security.authentication.configuration.JwtProperties;
import org.trustdeck.service.DomainDBAccessService;
import org.trustdeck.utils.SpringBeanLocator;

/**
 * Data Transfer Object (DTO) for permissions. This class represents the permissions of a user for a
 * specific domain and operation.
 *
 * @author Eric Wündisch
 */
@Data
@NoArgsConstructor
@Scope("prototype")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PermissionDTO implements IObjectDTO<String, PermissionDTO> {

  /**
   * The domain for which the permission applies.
   */
  String domain;

  /**
   * The operation associated with the permission.
   */
  String operation;

  /**
   * The user ID associated with the permission.
   */
  String userId;

  /**
   * Properties for the JWT configuration. Retrieved via SpringBeanLocator.
   */
  @JsonIgnore
  @Autowired
  private JwtProperties jwtProperties = SpringBeanLocator.getBean(JwtProperties.class);

  /**
   * Configuration of roles and operations. Retrieved via SpringBeanLocator.
   */
  @JsonIgnore
  @Autowired
  private RoleConfig roleConfig = SpringBeanLocator.getBean(RoleConfig.class);

  /**
   * Service for accessing domain database methods. Retrieved via SpringBeanLocator.
   */
  @JsonIgnore
  DomainDBAccessService domainDBAccessService = SpringBeanLocator.getBean(
      DomainDBAccessService.class);

  /**
   * Assigns values from a path string. The path is analyzed, and the values for domain and
   * operation are extracted.
   *
   * @param pojo The path string to be analyzed.
   * @return The updated PermissionDTO object or null if the path is invalid.
   */
  @Override
  public PermissionDTO assignPojoValues(String pojo) {

    if (pojo == null) {
      return null;
    }

    String path = pojo;
    if (pojo.startsWith("/" + jwtProperties.getDomainRoleGroupContextName())) {
      path = pojo.substring(("/" + jwtProperties.getDomainRoleGroupContextName()).length());
    }

    if (path.startsWith("/")) {
      path = path.substring(1);
    }

    String[] splitedPath = path.split("/");

    if (splitedPath.length == 2) {
      this.setOperation(splitedPath[0]);
      this.setDomain(splitedPath[1]);
    }
    return this;
  }

  /**
   * Checks whether the default view of the object is valid.
   *
   * @return true if the default view is valid, otherwise false.
   */
  @Override
  @JsonIgnore
  public Boolean isValidStandardView() {
    return this.validate();
  }

  /**
   * Reduces the object to its standard view.
   *
   * @return The reduced PermissionDTO object.
   */
  @Override
  @JsonIgnore
  public PermissionDTO toReducedStandardView() {
    return this;
  }

  /**
   * Returns a string representation of the object. The representation includes the values for
   * operation, domain, and user ID.
   *
   * @return A string representation of the object.
   */
  @Override
  @JsonIgnore
  public String toRepresentationString() {
    String out = "";

    out += (this.getOperation() != null) ? "operation: " + this.getOperation() + ", " : "";
    out += (this.getDomain() != null) ? "domain: " + this.getDomain() + ", " : "";
    out += (this.getDomain() != null) ? "userId: " + this.getUserId() + ", " : "";

    return (out.endsWith(", ") ? out.substring(0, out.length() - 2) : out);
  }

  /**
   * Validates the permission. Checks whether the operation is valid and whether the domain exists.
   *
   * @return true if the permission is valid, otherwise false.
   */
  @Override
  @JsonIgnore
  public Boolean validate() {
    return roleConfig.getOperations().contains(this.getOperation())
        && domainDBAccessService.getDomainByName(this.getDomain(), null) != null;
  }

  /**
   * Checks if the current object is equal to another object.
   *
   * @param o The object to compare with.
   * @return true if the objects are equal, otherwise false.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PermissionDTO that)) {
      return false;
    }
    return Objects.equals(getDomain(), that.getDomain()) && Objects.equals(
        getOperation(), that.getOperation()) && Objects.equals(getUserId(),
        that.getUserId());
  }

  /**
   * Returns the hash code of the object based on its domain, operation, and user ID.
   *
   * @return The hash code of the object.
   */
  @JsonIgnore
  public String getOperationPath() {
    return "/" + jwtProperties.getDomainRoleGroupContextName() + "/" + this.getOperation();
  }

  /**
   * Returns the full path for the domain, which includes the operation path and the domain name.
   *
   * @return The full domain path as a string.
   */
  @JsonIgnore
  public String getDomainPath() {
    return this.getOperationPath() + "/"
        + this.getDomain();
  }
}