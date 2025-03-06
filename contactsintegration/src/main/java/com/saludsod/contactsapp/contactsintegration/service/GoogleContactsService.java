package com.saludsod.contactsapp.contactsintegration.service;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class GoogleContactsService {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final RestTemplate restTemplate;
    private static final Logger logger = LoggerFactory.getLogger(GoogleContactsService.class);

    public GoogleContactsService(OAuth2AuthorizedClientService authorizedClientService, RestTemplate restTemplate) {
        this.authorizedClientService = authorizedClientService;
        this.restTemplate = restTemplate;
    }

    private String getAccessToken(OAuth2AuthenticationToken authentication) {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getName()
        );

        if (client == null || client.getAccessToken() == null) {
            throw new IllegalStateException("OAuth2AuthorizedClient or AccessToken is null");
        }

        return client.getAccessToken().getTokenValue();
    }

    /** 
     * Fetch Google Contacts
     */
    public String getContacts(OAuth2AuthenticationToken authentication) {
        try {
            String accessToken = getAccessToken(authentication);
            String url = "https://people.googleapis.com/v1/people/me/connections?personFields=names,emailAddresses,phoneNumbers";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() != HttpStatus.OK) {
                logger.error("Failed to fetch contacts: {}", response.getStatusCode());
                return "Error fetching contacts. Status: " + response.getStatusCode();
            }
            
            return response.getBody();
        } catch (Exception e) {
            logger.error("Error fetching contacts", e);
            return "Error fetching contacts: " + e.getMessage();
        }
    }

    /** 
     * Create New Contact
     */
    public String createContact(OAuth2AuthenticationToken authentication, String fullName, String email, String phoneNumber) {
        try {
            String accessToken = getAccessToken(authentication);
            String url = "https://people.googleapis.com/v1/people:createContact";

            String jsonBody = "{"
                    + "\"names\": [{\"givenName\": \"" + fullName + "\"}],"
                    + "\"emailAddresses\": [{\"value\": \"" + email + "\"}],"
                    + "\"phoneNumbers\": [{\"value\": \"" + phoneNumber + "\"}]"
                    + "}";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() != HttpStatus.OK) {
                logger.error("Failed to create contact: {}", response.getStatusCode());
                return "Error creating contact. Status: " + response.getStatusCode();
            }

            return response.getBody();
        } catch (Exception e) {
            logger.error("Error creating contact", e);
            return "Error creating contact: " + e.getMessage();
        }
    }

    /** 
     * Update Contact (Deletes old contact and creates a new one)
     */
    public String updateContact(OAuth2AuthenticationToken authentication, String resourceName, String fullName, String email, String phoneNumber) {
        try {
            // Step 1: Delete the existing contact
            String deleteResponse = deleteContact(authentication, resourceName);
            if (!deleteResponse.contains("successfully")) {
                return "Failed to update contact. Could not delete old contact.";
            }

            // Step 2: Create a new contact with updated details
            String createResponse = createContact(authentication, fullName, email, phoneNumber);
            return createResponse.contains("Error") ? "Failed to update contact: " + createResponse : "Contact updated successfully";
        } catch (Exception e) {
            logger.error("Error updating contact", e);
            return "Error updating contact: " + e.getMessage();
        }
    }

    /** 
     * Delete Contact
     */
    public String deleteContact(OAuth2AuthenticationToken authentication, String resourceName) {
        try {
            if (resourceName == null || resourceName.isEmpty()) {
                System.out.println("Invalid resourceName provided for deletion.");
                return "Error: Invalid resourceName.";
            }
    
            String accessToken = getAccessToken(authentication);
            String url = "https://people.googleapis.com/v1/" + resourceName + ":delete"; // Google API URL
    
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
    
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
    
            if (response.getStatusCode().is2xxSuccessful()) {
                return "Contact deleted successfully";
            } else {
                return "Failed to delete contact. Status: " + response.getStatusCode();
            }
        } catch (Exception e) {
            return "Error deleting contact: " + e.getMessage();
        }
    }
}
