CREATE TABLE news_world (
    id SERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    link TEXT NOT NULL,
    pub_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- Set the default value to the current time
    description TEXT, 
    source TEXT
);