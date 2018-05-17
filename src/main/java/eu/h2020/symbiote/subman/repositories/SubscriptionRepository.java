package eu.h2020.symbiote.subman.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;

import eu.h2020.symbiote.cloud.model.internal.Subscription;

/**
 * @author petarkrivic
 * 
 * MongoDB repository interface for Subscription objects providing CRUD operations.
 */
public interface SubscriptionRepository extends MongoRepository<Subscription, String>{
}
