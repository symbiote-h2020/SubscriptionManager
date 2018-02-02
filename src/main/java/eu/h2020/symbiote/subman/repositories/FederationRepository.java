package eu.h2020.symbiote.subman.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;

import eu.h2020.symbiote.model.mim.Federation;

/**
 * @author petarkrivic
 * 
 * MongoDB repository interface for Federation objects providing CRUD operations.
 */
public interface FederationRepository extends MongoRepository<Federation, String> {
}