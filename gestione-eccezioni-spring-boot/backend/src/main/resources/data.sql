INSERT INTO app_user (name) VALUES ('Ada');
INSERT INTO app_user (name) VALUES ('Linus');

INSERT INTO book (isbn, title, author) VALUES ('9788804668237', 'Il nome della rosa', 'Umberto Eco');
INSERT INTO book (isbn, title, author) VALUES ('9788806219352', 'Se questo è un uomo', 'Primo Levi');
INSERT INTO book (isbn, title, author) VALUES ('9788845292613', 'Il Gattopardo', 'Giuseppe Tomasi di Lampedusa');
INSERT INTO book (isbn, title, author) VALUES ('9780134685991', 'Effective Java', 'Joshua Bloch');
INSERT INTO book (isbn, title, author) VALUES ('9780321349606', 'Java Concurrency in Practice', 'Brian Goetz');
INSERT INTO book (isbn, title, author) VALUES ('9780201633610', 'Design Patterns', 'Erich Gamma');

-- Linus (id 2) ha già 3 prestiti attivi, sui libri 4, 5 e 6: è al limite.
INSERT INTO loan (book_id, user_id, loaned_at) VALUES (4, 2, CURRENT_TIMESTAMP);
INSERT INTO loan (book_id, user_id, loaned_at) VALUES (5, 2, CURRENT_TIMESTAMP);
INSERT INTO loan (book_id, user_id, loaned_at) VALUES (6, 2, CURRENT_TIMESTAMP);
