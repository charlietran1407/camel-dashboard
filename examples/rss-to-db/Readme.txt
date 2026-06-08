1. Database Table Creation
Ensure the target database has the following table structure before running the route:

```sql
CREATE TABLE news_world (
    id SERIAL PRIMARY KEY,
    title VARCHAR(500),
    link TEXT,
    pub_date TIMESTAMP,
    description TEXT,
    source VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

2. Deploy the Route
Ensure your environment already has a pre-configured DataSource bean named newsWorldDb.

Deploy the standalone rss-to-db.camel.yaml file to your Camel Dashboard.
