package com.saludsod.contactsapp.contactsintegration.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saludsod.contactsapp.contactsintegration.service.GoogleContactsService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/contacts")
public class ContactsController {

    private final GoogleContactsService googleContactsService;

    public ContactsController(GoogleContactsService googleContactsService) {
        this.googleContactsService = googleContactsService;
    }

    @GetMapping
    public String getContacts(Model model, OAuth2AuthenticationToken authentication) {
        try {
            String jsonResponse = googleContactsService.getContacts(authentication);

            String username = authentication.getPrincipal().getAttribute("name");
            if (username == null) {
                username = "User"; // Fallback if name is not available
            }

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(jsonResponse);

            List<Map<String, String>> contacts = new ArrayList<>();

            if (root.has("connections")) {
                for (JsonNode connection : root.get("connections")) {
                    String name = connection.has("names") ? 
                                  connection.get("names").get(0).get("displayName").asText() : 
                                  "Unknown";

                    String email = connection.has("emailAddresses") ? 
                                   connection.get("emailAddresses").get(0).get("value").asText() : 
                                   "No email";

                    String phone = connection.has("phoneNumbers") ? 
                                   connection.get("phoneNumbers").get(0).get("value").asText() : 
                                   "No phone";

                    String resourceName = connection.has("resourceName") ? 
                                          connection.get("resourceName").asText() : 
                                          "";

                    // Generate initials for profile icon
                    String[] nameParts = name.split(" ");
                    String initials = nameParts.length > 1 ? 
                                      nameParts[0].substring(0, 1) + nameParts[1].substring(0, 1) : 
                                      nameParts[0].substring(0, 1);
                    initials = initials.toUpperCase();

                    contacts.add(Map.of(
                        "name", name, 
                        "email", email, 
                        "phone", phone,
                        "initial", initials,
                        "resourceName", resourceName // Needed for update/delete
                    ));
                }
            }

            model.addAttribute("username", username);
            model.addAttribute("contacts", contacts);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to fetch contacts: " + e.getMessage());
        }

        return "contacts";
    }

    @PostMapping("/add")
    public String createContact(@RequestParam String name, @RequestParam String email, @RequestParam String phone, OAuth2AuthenticationToken authentication, Model model) {
        String response = googleContactsService.createContact(authentication, name, email, phone);
        model.addAttribute("message", response);
        return "redirect:/contacts"; // Refresh the contacts list
    }

    @PutMapping("/edit")
    public String updateContact(@RequestParam String resourceName, @RequestParam String name, @RequestParam String email, @RequestParam String phone, OAuth2AuthenticationToken authentication, Model model) {
        String response = googleContactsService.updateContact(authentication, resourceName, name, email, phone);
        model.addAttribute("message", response);
        return "redirect:/contacts";
    }

    // This method handles the deletion of contacts. Make sure the form sends a DELETE request.
    @RequestMapping(value = "/delete", method = RequestMethod.POST)
public String deleteContact(@RequestParam String resourceName, OAuth2AuthenticationToken authentication, Model model) {
    try {
        // Pass the resourceName to the service for deleting the contact
        String response = googleContactsService.deleteContact(authentication, resourceName);
        
        // Add the response message to be shown to the user
        model.addAttribute("message", response);
    } catch (Exception e) {
        // If there's an error, display the error message
        model.addAttribute("message", "Failed to delete contact: " + e.getMessage());
    }
    
    // Redirect back to the contacts list
    return "redirect:/contacts";
}
}