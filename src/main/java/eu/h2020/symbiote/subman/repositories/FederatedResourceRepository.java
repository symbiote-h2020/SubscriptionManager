package eu.h2020.symbiote.subman.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;

import eu.h2020.symbiote.cloud.model.internal.FederatedResource;

/**
 * @author petarkrivic
 * 
 * MongoDB repository interface for Federation objects providing CRUD operations.
 */
public interface FederatedResourceRepository extends MongoRepository<FederatedResource, String> {
}
