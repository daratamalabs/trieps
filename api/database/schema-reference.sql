-- SQLite schema reference only. No user data. For MySQL/PostgreSQL use Laravel migrations.
PRAGMA foreign_keys = ON;

CREATE TABLE "migrations" ("id" integer primary key autoincrement not null, "migration" varchar not null, "batch" integer not null);

CREATE TABLE "users" ("id" integer primary key autoincrement not null, "name" varchar not null, "email" varchar not null, "email_verified_at" datetime, "password" varchar not null, "remember_token" varchar, "created_at" datetime, "updated_at" datetime, "google_id" varchar);

CREATE TABLE "password_reset_tokens" ("email" varchar not null, "token" varchar not null, "created_at" datetime, primary key ("email"));

CREATE TABLE "sessions" ("id" varchar not null, "user_id" integer, "ip_address" varchar, "user_agent" text, "payload" text not null, "last_activity" integer not null, primary key ("id"));

CREATE TABLE "cache" ("key" varchar not null, "value" text not null, "expiration" integer not null, primary key ("key"));

CREATE TABLE "cache_locks" ("key" varchar not null, "owner" varchar not null, "expiration" integer not null, primary key ("key"));

CREATE TABLE "jobs" ("id" integer primary key autoincrement not null, "queue" varchar not null, "payload" text not null, "attempts" integer not null, "reserved_at" integer, "available_at" integer not null, "created_at" integer not null);

CREATE TABLE "job_batches" ("id" varchar not null, "name" varchar not null, "total_jobs" integer not null, "pending_jobs" integer not null, "failed_jobs" integer not null, "failed_job_ids" text not null, "options" text, "cancelled_at" integer, "created_at" integer not null, "finished_at" integer, primary key ("id"));

CREATE TABLE "failed_jobs" ("id" integer primary key autoincrement not null, "uuid" varchar not null, "connection" varchar not null, "queue" varchar not null, "payload" text not null, "exception" text not null, "failed_at" datetime not null default CURRENT_TIMESTAMP);

CREATE TABLE "vehicles" ("id" integer primary key autoincrement not null, "user_id" integer not null, "name" varchar not null, "brand" varchar not null, "model" varchar not null, "year" integer not null, "plate" varchar, "odometer" numeric not null default '0', "created_at" datetime, "updated_at" datetime, foreign key("user_id") references "users"("id") on delete cascade);

CREATE TABLE "service_rules" ("id" integer primary key autoincrement not null, "vehicle_id" integer not null, "component" varchar not null, "interval_km" integer not null, "interval_months" integer not null, foreign key("vehicle_id") references "vehicles"("id") on delete cascade);

CREATE TABLE "service_records" ("id" integer primary key autoincrement not null, "vehicle_id" integer not null, "component" varchar not null, "odometer" numeric not null, "serviced_at" date not null, "cost" numeric not null default '0', "notes" text, "created_at" datetime, "updated_at" datetime, foreign key("vehicle_id") references "vehicles"("id") on delete cascade);

CREATE TABLE "tours" ("id" integer primary key autoincrement not null, "owner_id" integer not null, "title" varchar not null, "mode" varchar not null, "status" varchar not null default 'planned', "start_name" varchar not null, "finish_name" varchar not null, "invite_token" varchar not null, "created_at" datetime, "updated_at" datetime, foreign key("owner_id") references "users"("id") on delete cascade);

CREATE TABLE "tour_members" ("id" integer primary key autoincrement not null, "tour_id" integer not null, "user_id" integer not null, "vehicle_id" integer not null, "start_odo" numeric not null, "finish_odo" numeric, "started_at" datetime, "finished_at" datetime, foreign key("tour_id") references "tours"("id") on delete cascade, foreign key("user_id") references "users"("id") on delete cascade, foreign key("vehicle_id") references "vehicles"("id"));

CREATE TABLE "checkpoints" ("id" integer primary key autoincrement not null, "tour_id" integer not null, "name" varchar not null, "latitude" numeric, "longitude" numeric, "position" integer not null default '0', foreign key("tour_id") references "tours"("id") on delete cascade);

CREATE TABLE "checkpoint_visits" ("id" integer primary key autoincrement not null, "checkpoint_id" integer not null, "user_id" integer not null, "odometer" numeric not null, "visited_at" datetime not null, foreign key("checkpoint_id") references "checkpoints"("id") on delete cascade, foreign key("user_id") references "users"("id") on delete cascade);

CREATE TABLE "track_points" ("id" integer primary key autoincrement not null, "tour_id" integer not null, "user_id" integer not null, "client_id" varchar not null, "latitude" numeric not null, "longitude" numeric not null, "accuracy" float not null, "recorded_at" datetime not null, foreign key("tour_id") references "tours"("id") on delete cascade, foreign key("user_id") references "users"("id") on delete cascade);

CREATE TABLE "fuel_entries" ("id" integer primary key autoincrement not null, "tour_id" integer not null, "user_id" integer not null, "odometer" numeric not null, "liters" numeric not null, "cost" numeric not null default '0', "full_tank" tinyint(1) not null default '0', "created_at" datetime, "updated_at" datetime, foreign key("tour_id") references "tours"("id") on delete cascade, foreign key("user_id") references "users"("id") on delete cascade);

CREATE TABLE "communities" ("id" integer primary key autoincrement not null, "owner_id" integer not null, "name" varchar not null, "description" text, "invite_token" varchar not null, "created_at" datetime, "updated_at" datetime, foreign key("owner_id") references "users"("id") on delete cascade);

CREATE TABLE "community_members" ("id" integer primary key autoincrement not null, "community_id" integer not null, "user_id" integer not null, "status" varchar not null, foreign key("community_id") references "communities"("id") on delete cascade, foreign key("user_id") references "users"("id") on delete cascade);

CREATE TABLE "posts" ("id" integer primary key autoincrement not null, "user_id" integer not null, "tour_id" integer, "caption" text not null, "photos" text, "route_card" text, "created_at" datetime, "updated_at" datetime, foreign key("user_id") references "users"("id") on delete cascade, foreign key("tour_id") references "tours"("id") on delete set null);

CREATE TABLE "post_likes" ("id" integer primary key autoincrement not null, "post_id" integer not null, "user_id" integer not null, foreign key("post_id") references "posts"("id") on delete cascade, foreign key("user_id") references "users"("id") on delete cascade);

CREATE TABLE "waitlist" ("id" integer primary key autoincrement not null, "email" varchar not null, "created_at" datetime, "updated_at" datetime);

CREATE TABLE "personal_access_tokens" ("id" integer primary key autoincrement not null, "tokenable_type" varchar not null, "tokenable_id" integer not null, "name" text not null, "token" varchar not null, "abilities" text, "last_used_at" datetime, "expires_at" datetime, "created_at" datetime, "updated_at" datetime);

CREATE UNIQUE INDEX "users_email_unique" on "users" ("email");

CREATE INDEX "sessions_user_id_index" on "sessions" ("user_id");

CREATE INDEX "sessions_last_activity_index" on "sessions" ("last_activity");

CREATE INDEX "cache_expiration_index" on "cache" ("expiration");

CREATE INDEX "cache_locks_expiration_index" on "cache_locks" ("expiration");

CREATE INDEX "jobs_queue_index" on "jobs" ("queue");

CREATE INDEX "failed_jobs_connection_queue_failed_at_index" on "failed_jobs" ("connection", "queue", "failed_at");

CREATE UNIQUE INDEX "failed_jobs_uuid_unique" on "failed_jobs" ("uuid");

CREATE UNIQUE INDEX "users_google_id_unique" on "users" ("google_id");

CREATE UNIQUE INDEX "service_rules_vehicle_id_component_unique" on "service_rules" ("vehicle_id", "component");

CREATE UNIQUE INDEX "tours_invite_token_unique" on "tours" ("invite_token");

CREATE UNIQUE INDEX "tour_members_tour_id_user_id_unique" on "tour_members" ("tour_id", "user_id");

CREATE UNIQUE INDEX "checkpoint_visits_checkpoint_id_user_id_unique" on "checkpoint_visits" ("checkpoint_id", "user_id");

CREATE UNIQUE INDEX "track_points_user_id_client_id_unique" on "track_points" ("user_id", "client_id");

CREATE INDEX "track_points_tour_id_user_id_recorded_at_index" on "track_points" ("tour_id", "user_id", "recorded_at");

CREATE UNIQUE INDEX "communities_invite_token_unique" on "communities" ("invite_token");

CREATE UNIQUE INDEX "community_members_community_id_user_id_unique" on "community_members" ("community_id", "user_id");

CREATE UNIQUE INDEX "post_likes_post_id_user_id_unique" on "post_likes" ("post_id", "user_id");

CREATE UNIQUE INDEX "waitlist_email_unique" on "waitlist" ("email");

CREATE INDEX "personal_access_tokens_tokenable_type_tokenable_id_index" on "personal_access_tokens" ("tokenable_type", "tokenable_id");

CREATE UNIQUE INDEX "personal_access_tokens_token_unique" on "personal_access_tokens" ("token");

CREATE INDEX "personal_access_tokens_expires_at_index" on "personal_access_tokens" ("expires_at");
