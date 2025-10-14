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
import lombok.Data;
import lombok.NoArgsConstructor;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.context.annotation.Scope;

/**
 * Data Transfer Object (DTO) for an employee. This class represents the employee's details such as
 * user ID, username, first name, last name, email, federation, and federation ID. It implements the
 * IObjectDTO interface to provide methods for assigning values from a POJO, checking validity, and
 * converting to a reduced view.
 *
 * @author Eric Wündisch
 */
@Data
@NoArgsConstructor
@Scope("prototype")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OperatorDTO implements IObjectDTO<UserRepresentation, OperatorDTO> {

  /**
   * The unique identifier of the operator. This field is used to store the user ID for
   * identification purposes.
   */
  private String userId;

  /**
   * The username of the operator. This field is used to store the operator's username for
   * identification purposes.
   */
  private String username;

  /**
   * The first name of the operator. This field is used to store the operator's first name for
   * identification purposes.
   */
  private String firstName;

  /**
   * The last name of the operator. This field is used to store the operator's last name for
   * identification purposes.
   */
  private String lastName;

  /**
   * The email address of the operator. This field is used to store the operator's email for
   * communication purposes.
   */
  private String email;

  /**
   * The federation associated with the operator. This field is used to indicate the federation to
   * which the operator belongs.
   */
  private String federation;

  /**
   * The federation ID associated with the operator. This field is used to link the operator to a
   * specific federation.
   */
  private String federationId;

  /**
   * Assigns values from the given UserRepresentation POJO to the OperatorDTO object. This method
   * sets the userId, username, firstName, lastName, email, and federationId fields based on the
   * provided UserRepresentation.
   *
   * @param pojo the UserRepresentation object containing user details
   * @return null, as this method does not return any value
   */
  @Override
  @JsonIgnore
  public OperatorDTO assignPojoValues(UserRepresentation pojo) {

    this.setUserId(pojo.getId());
    this.setUsername(pojo.getUsername());
    this.setFirstName(pojo.getFirstName());
    this.setLastName(pojo.getLastName());
    this.setEmail(pojo.getEmail());
    this.setFederationId(pojo.getFederationLink());

    return null;
  }

  /**
   * Checks if the OperatorDTO object is valid for the standard view. This method currently returns
   * true, indicating that the object is valid.
   *
   * @return true, indicating that the object is valid for the standard view.
   */
  @Override
  @JsonIgnore
  public Boolean isValidStandardView() {
    return true;
  }

  /**
   * Reduces the OperatorDTO object to its minimal view.
   *
   * @return this, indicating that the object itself is returned
   */
  @Override
  @JsonIgnore
  public OperatorDTO toReducedStandardView() {
    return this;
  }

  /**
   * Converts the OperatorDTO object to a string representation. This method currently returns null
   * as no specific representation is defined.
   *
   * @return null, indicating no string representation is provided.
   */
  @Override
  @JsonIgnore
  public String toRepresentationString() {
    String out = "";

    out += (this.getUserId() != null) ? "userId: " + this.getUserId() + ", " : "";
    out += (this.getUsername() != null) ? "username: " + this.getUsername() + ", " : "";
    out += (this.getFirstName() != null) ? "firstName: " + this.getFirstName() + ", " : "";
    out += (this.getLastName() != null) ? "lastName: " + this.getLastName() + ", " : "";
    out += (this.getEmail() != null) ? "email: " + this.getEmail() + ", " : "";
    out += (this.getFederationId() != null) ? "federationId: " + this.getFederationId() + ", " : "";
    out += (this.getFederation() != null) ? "federation: " + this.getFederation() + ", " : "";

    return (out.endsWith(", ") ? out.substring(0, out.length() - 2) : out);
  }

  /**
   * Validates the OperatorDTO object. This method is currently a placeholder and does not perform
   * any validation.
   *
   * @return null, indicating no validation is performed.
   */
  @Override
  @JsonIgnore
  public Boolean validate() {
    return true;
  }
}
