ALTER TABLE brands
    ADD COLUMN file_size_id UUID REFERENCES files(id);
